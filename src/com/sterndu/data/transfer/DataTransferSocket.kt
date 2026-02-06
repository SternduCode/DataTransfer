package com.sterndu.data.transfer

import com.sterndu.multicore.LoggingUtil
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

open class DataTransferSocket(val socket: java.net.Socket = java.net.Socket(), secureMode: Boolean = false, host: Boolean = false) : DataTransferClient(secureMode) {

	private var logger: Logger = LoggingUtil.getLogger(BASIC_SOCKET)

	override var appendix: String = "uninitialized socket"

	init {
		allSockets[this] = Thread.currentThread().stackTrace.let { it.copyOfRange(1, 4.coerceAtMost(it.size)) }
		if (socket.isBound) {
			appendix = "${socket.inetAddress}:${socket.port} -- ${socket.localAddress}:${socket.localPort}".replace("/", "").replace(":", "-")
			init(host)
		}
	}

	@Deprecated("Use constructor parameter instead")
	fun initWithHost(host: Boolean) {
		appendix = "${socket.inetAddress}:${socket.port} -- ${socket.localAddress}:${socket.localPort}".replace("/", "").replace(":", "-")
		super.init(host)
	}

	override val isClosed: Boolean
    	get() = socket.isClosed

	override val isDataAvailable: Boolean
		get() = socket.inputStream.available() > 0

	override val isConnected: Boolean
		get() = socket.isConnected

	override fun name(withClassName: Boolean) = if (withClassName) "${javaClass.simpleName} ${socket.inetAddress}:${socket.port}" else "${socket.inetAddress}:${socket.port}"

	/**
	 * Receive data.
	 *
	 * @return the packet
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
    override fun receiveData(): Packet {
        val md = md ?: throw IllegalStateException()

        var packet: Packet
		var recvStamp = 0L
		try {
			recvStamp = recvLock.writeLock() // Get an exclusive lock for receiving

			var b = ByteArray(5)
			if (readXBytes(b, socket.inputStream, b.size, 5000)) {
				val type = b[0]
				val length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1)
                if (length in 0..MAX_PACKET_SIZE) {
                    b = ByteArray(32)
                    var data = ByteArray(length)
                    if (readXBytes(data, socket.inputStream, length, 5000 + length * 10L)
                        && readXBytes(b, socket.inputStream, b.size, 5000 + 320) && b.contentEquals(md.digest(data))
                    ) {
                        packet = Packet(type, data)
                        if (data.size > 5000) data = data.copyOfRange(0, 5000)
                        logReceiveState(type, 'r', length, data, b)
                    } else {
                        if (data.size > 5000) data = data.copyOfRange(0, 5000)
                        logReceiveState(type, 'f', length, data, b)
                        return Packet(((-128).toByte()), ByteArray(0))
                    }
                } else {
					logger.fine("$type f[length: $length]")
					return Packet(((-128).toByte()), ByteArray(0))
				}
			} else {
				logger.fine("f" + b.contentToString())
				return Packet(((-128).toByte()), ByteArray(0))
			}
		} finally {
			recvLock.unlock(recvStamp)
		}
		return Packet(packet.type, implReceiveData(packet.type, packet.data))
		// type byte; length int; data byte[]; hash byte[32];
	}

	private fun logReceiveState(type: Byte, state: Char, length: Int, data: ByteArray, hash: ByteArray) {
		if (state == 'r')
			logger.finest("$type $state[length: $length,data: ${data.contentToString()},hash: ${hash.contentToString()}")
		else
			logger.fine("$type $state[length: $length,data: ${data.contentToString()},hash: ${hash.contentToString()}")
	}

	/**
	 * Send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @param raw true, if raw data should be sent
	 * @throws java.net.SocketException the socket exception
	 */
	@Throws(SocketException::class)
	override fun sendData(type: Byte, data: ByteArray, raw: Boolean) {
		if (!raw && !initialized) {
			delayedSend.add(Packet(type, data))
			return
		}
		if (isClosed) throw SocketException(SOCKET_CLOSED)
		val md = md ?: throw IllegalStateException()
		var sendStamp = 0L
		try {
			sendStamp = sendLock.writeLock() // Get an exclusive lock for sending

			if (isClosed) throw SocketException(SOCKET_CLOSED)
			Files.write(File("./${appendix}_${System.currentTimeMillis()}_${type}${if (raw) "I" else ""}S.pckt").toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.WRITE) //write content -> appendix_timestamp.pckt
			val modifiedData = if (raw) data else implSendData(type, data)
			val hash = md.digest(modifiedData)
			val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(modifiedData.size).array()
			try {
				val os = socket.outputStream
				os.write(type.toInt())
				os.write(lengthBytes)
				os.write(modifiedData)
				os.write(hash)
				logger.finest(type.toString() + "${if (raw) "i" else ""}s[length_bytes:" + lengthBytes.contentToString() + ", length:" + modifiedData.size +
						",data:" + modifiedData.contentToString() + ",hash:" + hash.contentToString()
				)
			} catch (e: SocketException) {
				try {
					close()
				} catch (ex: IOException) {
					ex.initCause(e)
					logger.log(Level.WARNING, BASIC_SOCKET, ex)
				}
				if (e.message != "Broken pipe")
					throw e
				else logger.log(Level.FINEST, BASIC_SOCKET, e)
			} catch (e: IOException) {
				logger.log(Level.WARNING, BASIC_SOCKET, e)
				delayedSend.add(Packet(type, data))
			}
		} finally {
			sendLock.unlock(sendStamp)
		}
	}

	/**
	 * Close.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SocketException the socket exception
	 */
	@Throws(IOException::class, SocketException::class)
	override fun close() {
		if (logger == null) {
			logger = LoggingUtil.getLogger(BASIC_SOCKET)
			logger.info("FFS needed late init logger")
		}
		logger.fine("close $this ${Thread.currentThread().stackTrace.contentToString()}")
		try {
			var recvStamp = 0L
			var sendStamp = 0L

			try {
				recvStamp = recvLock.writeLock() // Get an exclusive lock for receiving
				sendStamp = sendLock.writeLock() // Get an exclusive lock for sending

				try {
					if (!isClosed) {
						shutdownHook(this)
						socket.shutdownOutput()
						socket.shutdownInput()
						removeDefaultUpdaterTasks()
						disablePeriodicPing()
						socket.close()
					}
				} catch (e: SocketException) {
					socket.close()
				}
			} finally {
				recvLock.unlock(recvStamp)
				sendLock.unlock(sendStamp)
			}
		} catch (_: NullPointerException) {
            removeDefaultUpdaterTasks()
            disablePeriodicPing()
			socket.close()
		}
	}

	companion object {

		private const val MAX_PACKET_AMOUNT_FOR_RESEND = 10

		private const val BASIC_SOCKET = "Basic Socket"

		private const val SOCKET_CLOSED = "Socket closed!"

		private const val MAX_PACKET_SIZE = 1_073_741_824 // 2^30

		val allSockets: MutableMap<DataTransferSocket, Array<StackTraceElement>> = Collections.synchronizedMap(HashMap())
	}

}