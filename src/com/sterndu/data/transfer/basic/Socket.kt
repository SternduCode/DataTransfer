@file:JvmName("Socket")
package com.sterndu.data.transfer.basic

import com.sterndu.data.transfer.DataTransferSocket
import com.sterndu.data.transfer.Packet
import com.sterndu.multicore.LoggingUtil
import com.sterndu.multicore.Updater
import com.sterndu.util.interfaces.ThrowingRunnable
import com.sterndu.util.readXBytes
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.locks.StampedLock
import java.util.logging.Level
import java.util.logging.Logger

open class Socket : DataTransferSocket {

	protected lateinit var appendix: String

	private var packetCounterSend: Short = 0
	private var packetCounterReceive: Short = 0
	private val resendList = HashMap<Short, Packet>()

	private var logger: Logger = LoggingUtil.getLogger(basicSocket)

	open var isHost = false
		protected set

	private val pingLock = StampedLock()

	private val lastPings = ArrayList<Long>()

	private var pingStartTime = 0L

	/**
	 * Instantiates a new socket.
	 *
	 */
	constructor() {
		allSockets[this] = Thread.currentThread().stackTrace.let { it.copyOfRange(1, 4.coerceAtMost(it.size)) }
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	constructor(address: InetAddress, port: Int) : super(address, port) {
		allSockets[this] = Thread.currentThread().stackTrace.let { it.copyOfRange(1, 4.coerceAtMost(it.size)) }
		appendix = "$inetAddress:$port -- $localAddress:$localPort".replace("/", "").replace(":", "-")
		init(false)
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @param localAddr the local addr
	 * @param localPort the local port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	constructor(address: InetAddress, port: Int, localAddr: InetAddress, localPort: Int) : super(
		address,
		port,
		localAddr,
		localPort
	) {
		allSockets[this] = Thread.currentThread().stackTrace.let { it.copyOfRange(1, 4.coerceAtMost(it.size)) }
		appendix = "$inetAddress:$port -- $localAddress:$localPort".replace("/", "").replace(":", "-")
		init(false)
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @throws UnknownHostException the unknown host exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class, UnknownHostException::class)
	constructor(host: String, port: Int) : super(host, port) {
		allSockets[this] = Thread.currentThread().stackTrace.let { it.copyOfRange(1, 4.coerceAtMost(it.size)) }
		appendix = "$inetAddress:$port -- $localAddress:$localPort".replace("/", "").replace(":", "-")
		init(false)
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @param localAddr the local addr
	 * @param localPort the local port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	constructor(host: String, port: Int, localAddr: InetAddress, localPort: Int) : super(
		host,
		port,
		localAddr,
		localPort
	) {
		allSockets[this] = Thread.currentThread().stackTrace.let { it.copyOfRange(1, 4.coerceAtMost(it.size)) }
		appendix = "$inetAddress:$port -- $localAddress:$localPort".replace("/", "").replace(":", "-")
		init(false)
	}

	/**
	 * Impl receive data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	protected open fun implReceiveData(type: Byte, data: ByteArray): ByteArray {
		return data
	}

	/**
	 * Impl send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	protected open fun implSendData(type: Byte, data: ByteArray): ByteArray {
		return data
	}

	private fun pingReceived() {
		val roundTripTime = System.currentTimeMillis() - pingStartTime
		pingStartTime = 0L
		lastPings.add(roundTripTime)
		if (lastPings.size > 32) lastPings.removeAll(lastPings.subList(0, lastPings.size - 32).toSet())
	}

	internal fun internalInit(host: Boolean) {
		appendix = "$inetAddress:$port -- $localAddress:$localPort".replace("/", "").replace(":", "-")
		init(host)
	}

	/**
	 * Init
	 *
	 * @param host if the Socket is in Host mode
	 */
	protected open fun init(host: Boolean) {
		if (logger == null)
			logger = LoggingUtil.getLogger(basicSocket)
		try {
			isHost = host
			md = MessageDigest.getInstance("SHA-512/256") // Better performance than SHA-256
			setHandle(((-1).toByte())) { _: Byte, _: ByteArray ->
				try {
					if (!isClosed) {
						logger.fine("close recv $this")
						close()
					}
				} catch (e: IOException) {
					logger.log(Level.WARNING, basicSocket, e)
				}
			}
			setHandle((-127).toByte()) { _: Byte, data: ByteArray ->
				if (!isClosed) {
					if (String(data, Charsets.UTF_8) == "Ping")
						sendData((-127).toByte(), "Pong".toByteArray(Charsets.UTF_8))
					else pingReceived()
				}
			}
			setHandle((-126).toByte()) { _: Byte, data: ByteArray ->
				if (!isClosed) {
					if (String(data, Charsets.UTF_8) == "Ping")
						sendInternalData((-126).toByte(), "Pong".toByteArray(Charsets.UTF_8))
					else pingReceived()
				}
			}
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, basicSocket, e)
		}
		Updater.add(ThrowingRunnable {
			if (!isClosed && inputStream.available() > 0) try {
				val data = receiveData()
				if (handles.containsKey(data.type)) getHandle(data.type)!!(data.type, data.data) else receiveQueue.add(data)
			} catch (e: IOException) {
				logger.log(Level.WARNING, basicSocket, e)
			}
			if (delayedSend.isNotEmpty() && initialized && !isClosed) {
				val (type, data) = delayedSend.removeAt(0)
				sendData(type, data)
			}
		}, "CheckForMsgs $appendix")
		Updater.add(ThrowingRunnable {
			if (!isClosed && pingStartTime != 0L && System.currentTimeMillis() - pingStartTime >= 5000) {
				try {
					sendClose()
				} catch (e: SocketException) {
					logger.finer(socketAlreadyClosed)
					disablePeriodicPing()
				}
				close()
			}
		}, "PingKill $appendix")
	}

	fun name(withClassName: Boolean = false) = if (withClassName) "${javaClass.simpleName} ${inetAddress}:${port}" else "${inetAddress}:${port}"

	/**
	 * Receive data.
	 *
	 * @return the packet
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	protected fun receiveData(): Packet {
		var packet: Packet
		var recvStamp = 0L
		try {
			recvStamp = recvLock.writeLock() // Get an exclusive lock for receiving

			var b = ByteArray(5)
			if (readXBytes(b, inputStream, b.size, 5000)) {
				val type = b[0]
				val length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1)
				b = ByteArray(32)
				var data = ByteArray(length)
				if (readXBytes(data, inputStream, length, 5000 + length * 1000L)
					&& readXBytes(b, inputStream, b.size, 5000 + 32000) && b.contentEquals(md!!.digest(data))
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
	 * Send internal data.
	 *
	 * @param type the type
	 * @param data the data
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	protected fun sendInternalData(type: Byte, data: ByteArray) {
		if (isClosed) throw SocketException(socketClosed)
		var sendStamp = 0L
		try {
			sendStamp = sendLock.writeLock() // Get an exclusive lock for sending

			if (isClosed) throw SocketException(socketClosed)
			val modifiedData = data
			val hash = md!!.digest(modifiedData)
			val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(modifiedData.size).array()
			try {
				val os = outputStream
				os.write(type.toInt())
				os.write(lengthBytes)
				os.write(modifiedData)
				os.write(hash)
				logger.finest(
					type.toString() + "s[length_bytes:" + lengthBytes.contentToString() + ", length:"
							+ modifiedData.size
							+ ",data:" + modifiedData.contentToString() + ",hash:" + hash.contentToString()
				)
			} catch (e: SocketException) {
				if (e.message != "Broken pipe")
					throw e
				else logger.log(Level.FINEST, basicSocket, e)
			} catch (e: IOException) {
				logger.log(Level.WARNING, basicSocket, e)
				delayedSend.add(Packet(type, data))
				return
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
		if (logger == null)
			logger = LoggingUtil.getLogger(basicSocket)
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
						shutdownOutput()
						shutdownInput()
						Updater.remove("CheckForMsgs $appendix")
						Updater.remove("PingKill $appendix")
						disablePeriodicPing()
						super.close()
					}
				} catch (e: SocketException) {
					super.close()
				}
			} finally {
				recvLock.unlock(recvStamp)
				sendLock.unlock(sendStamp)
			}
		} catch (e: NullPointerException) {
			if (appendix != null) {
				Updater.remove("CheckForMsgs $appendix")
				Updater.remove("PingKill $appendix")
				disablePeriodicPing()
			}
			super.close()
		}
	}

	fun disablePeriodicPing() {
		Updater.remove("Ping $appendix")
		pingStartTime = 0L
	}

	/**
	 * Gets the handle.
	 *
	 * @param type the type
	 * @return the handle
	 */
	fun getHandle(type: Byte): ((Byte, ByteArray) -> Unit)? {
		return handles[type]?.second
	}

	val messageCount: Int
		get() = receiveQueue.size
	val messageFromBuffer: Packet
		get() {
			if (receiveQueue.isEmpty()) throw EmptyStackException()
			return receiveQueue.removeFirst()
		}

	/**
	 * Checks for handle.
	 *
	 * @param type the type
	 * @return true, if successful
	 */
	fun hasHandle(type: Byte): Boolean {
		return handles.containsKey(type)
	}

	/**
	 * Checks for message.
	 *
	 * @return true, if successful
	 */
	fun hasMessage(): Boolean {
		return receiveQueue.isNotEmpty()
	}

	/**
	 * Request resend.
	 *
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun requestResend() {//TODO not implemented
		sendInternalData(0.toByte(), ByteArray(0))
	}

	fun getAveragePingTime() = lastPings.average()

	fun getLastPingTime() = lastPings.last()

	@Throws(SocketException::class)
	fun ping() {
		if (isClosed || !isConnected || !initialized) return
		var pingStamp = 0L
		try {
			pingStamp = pingLock.writeLock() // Get an exclusive lock for pinging

			if (isClosed || !isConnected || !initialized) return
			pingStartTime = System.currentTimeMillis()
			try {
				sendData((-127).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				logger.finer(socketAlreadyClosed)
				Updater.remove("Ping $appendix")
			}
		} finally {
			pingLock.unlock(pingStamp)
		}
	}

	@Throws(SocketException::class)
	fun internalPing() {
		if (isClosed || !isConnected || !initialized) return
		var pingStamp = 0L
		try {
			pingStamp = pingLock.writeLock() // Get an exclusive lock for pinging

			if (isClosed || !isConnected || !initialized) return
			pingStartTime = System.currentTimeMillis()
			try {
				sendInternalData((-126).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				logger.finer(socketAlreadyClosed)
				Updater.remove("Ping $appendix")
			}
		} finally {
			pingLock.unlock(pingStamp)
		}
	}

	fun setupPeriodicInternalPing(millis: Long = 100) {
		Updater.add(Runnable {
			if (!isClosed) {
				if (pingStartTime == 0L) {
					Thread {
						internalPing()
					}.start()
				}
			} else {
				disablePeriodicPing()
			}
		}, "Ping $appendix", millis)
		Thread {
			internalPing()
		}.start()
	}

	fun setupPeriodicPing(millis: Long = 100) {
		Updater.add(Runnable {
			if (!isClosed) {
				if (pingStartTime == 0L) {
					Thread {
						ping()
					}.start()
				}
			} else {
				disablePeriodicPing()
			}
		}, "Ping $appendix", millis)
		Thread {
			ping()
		}.start()
	}

	/**
	 * Send close.
	 *
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun sendClose() {
		logger.log(Level.WARNING, "send close $appendix $this", Exception("send close"))
		if (!isClosed && isConnected) sendInternalData((-1).toByte(), ByteArray(0))
	}

	/**
	 * Send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun sendData(type: Byte, data: ByteArray) {
		if (!initialized) {
			delayedSend.add(Packet(type, data))
			return
		}
		if (isClosed) throw SocketException(socketClosed)
		var sendStamp = 0L
		try {
			sendStamp = sendLock.writeLock() // Get an exclusive lock for sending

			if (isClosed) throw SocketException(socketClosed)
			val modifiedData = implSendData(type, data)
			val hash = md!!.digest(modifiedData)
			val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(modifiedData.size).array()
			try {
				val os = outputStream
				os.write(type.toInt())
				os.write(lengthBytes)
				os.write(modifiedData)
				os.write(hash)
				logger.finest(type.toString() + "s[length_bytes:" + lengthBytes.contentToString() + ", length:" + modifiedData.size +
						",data:" + modifiedData.contentToString() + ",hash:" + hash.contentToString()
				)
			} catch (e: SocketException) {
				try {
					close()
				} catch (ex: IOException) {
					ex.initCause(e)
					logger.log(Level.WARNING, basicSocket, ex)
				}
			} catch (e: IOException) {
				logger.log(Level.WARNING, basicSocket, e)
				delayedSend.add(Packet(type, data))
			}
		} finally {
			sendLock.unlock(sendStamp)
		}
	}

	private fun equalsOrNull(other: Class<*>?, clazz: Class<*>): Boolean {
		return other == null || clazz == other
	}

	/**
	 * Sets the handle.
	 *
	 * @param type the type
	 * @param handle the handle
	 * @return true, if successful
	 */
	fun setHandle(type: Byte, handle: ((Byte, ByteArray) -> Unit)?): Boolean {
		return try {
			synchronized(this) {
				val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
				if (equalsOrNull(handles[type]?.first, caller)) {
					if (handle != null) {
						if (!handles.containsKey(type)) {
							val it = receiveQueue.iterator()
							it.forEach { (type1, data) ->
								if (type1 == type) {
									handle(type1, data)
									it.remove()
								}
							}
						}
						handles[type] = caller to handle
					} else handles.remove(type)
					return true
				}
				false
			}
		} catch (e: Exception) {
			logger.log(Level.WARNING, basicSocket, e)
			false
		}
	}

	override var shutdownHook: (DataTransferSocket) -> Unit
		get() = super.shutdownHook
		set(value) {
			super.shutdownHook = value
		}

	companion object {

		private const val MAX_PACKET_AMOUNT_FOR_RESEND = 10

		private const val basicSocket = "Basic Socket"

		private const val socketAlreadyClosed = "Socket already closed"

		private const val socketClosed = "Socket closed!"

		val allSockets: MutableMap<Socket, Array<StackTraceElement>> = Collections.synchronizedMap(HashMap())
	}

}
