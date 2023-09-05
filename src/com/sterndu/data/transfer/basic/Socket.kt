@file:JvmName("Socket")
package com.sterndu.data.transfer.basic

import com.sterndu.data.transfer.DatatransferSocket
import com.sterndu.data.transfer.Packet
import com.sterndu.multicore.LoggingUtil
import com.sterndu.multicore.Updater
import com.sterndu.util.interfaces.ThrowingRunnable
import com.sterndu.util.readXBytes
import java.io.IOException
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.ArrayList

open class Socket : DatatransferSocket {

	private val logger: Logger

	open var isHost = false
		protected set

	private val pingLock = Any()

	private val lastPings = ArrayList<Long>()

	private var pingReceived = 0L

	/**
	 * Instantiates a new socket.
	 *
	 */
	constructor() {
		logger = LoggingUtil.getLogger(basicSocket)
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
		logger = LoggingUtil.getLogger(basicSocket)
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
		logger = LoggingUtil.getLogger(basicSocket)
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
		logger = LoggingUtil.getLogger(basicSocket)
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
		logger = LoggingUtil.getLogger(basicSocket)
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

	internal fun internalInit(host: Boolean) {
		init(host)
	}

	/**
	 * Init
	 *
	 * @param host if the Socket is in Host mode
	 */
	protected open fun init(host: Boolean) {
		try {
			isHost = host
			md = MessageDigest.getInstance("SHA-256") // SHA3-256
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
					else pingReceived = System.currentTimeMillis()
				}
			}
			setHandle((-126).toByte()) { _: Byte, data: ByteArray ->
				if (!isClosed) {
					if (String(data, Charsets.UTF_8) == "Ping")
						sendInternalData((-126).toByte(), "Pong".toByteArray(Charsets.UTF_8))
					else pingReceived = System.currentTimeMillis()
				}
			}
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, basicSocket, e)
		}
		Updater.add(ThrowingRunnable {
			if (!isClosed && inputStream.available() > 0) try {
				val data = receiveData()
				if (handles.containsKey(data.type)) getHandle(data.type)!!(data.type, data.data) else recvVector.add(data)
			} catch (e: IOException) {
				logger.log(Level.WARNING, basicSocket, e)
			}
			if (delayedSend.isNotEmpty() && initialized && !isClosed) {
				val (type, data1) = delayedSend.removeAt(0)
				sendData(type, data1)
			}
		}, "CheckForMsgs" + hashCode())
	}

	/**
	 * Receive data.
	 *
	 * @return the packet
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	protected fun receiveData(): Packet {
		var packet: Packet
		synchronized(recLock) {
			var b = ByteArray(5)
			if (readXBytes(b, inputStream, b.size, 5000)) {
				val type = b[0]
				val length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1)
				b = ByteArray(32)
				var data = ByteArray(length)
				if (readXBytes(data, inputStream, length, 5000)
					&& readXBytes(b, inputStream, b.size, 5000) && b.contentEquals(md!!.digest(data))
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
		}
		return Packet(packet.type, implReceiveData(packet.type, packet.data))
		// type byte; length int; data byte[]; hash byte[32];
	}

	private fun logReceiveState(type: Byte, state: Char, length: Int, data: ByteArray, hash: ByteArray) {
		if (state == 'r')
			logger.finest(type.toString() + "$type $state[length: $length,data: ${data.contentToString()},hash: ${hash.contentToString()}")
		else
			logger.fine(type.toString() + "$type $state[length: $length,data: ${data.contentToString()},hash: ${hash.contentToString()}")
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
		synchronized(sendLock) {
			if (isClosed) return
			val modifiedData = implSendData(type, data)
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
		logger.fine("close ${Thread.currentThread().stackTrace.contentToString()}")
		try {
			synchronized(recLock) {
				synchronized(sendLock) {
					try {
						if (!isClosed) {
							shutdownHook(this)
							shutdownOutput()
							shutdownInput()
							Updater.remove("CheckForMsgs" + hashCode())
							disablePeriodicPing()
							super.close()
						}
					} catch (e: SocketException) {
						super.close()
					}
				}
			}
		} catch (e: NullPointerException) {
			Updater.remove("CheckForMsgs" + hashCode())
			disablePeriodicPing()
			super.close()
		}
	}

	fun disablePeriodicPing() {
		Updater.remove("Ping" + hashCode())
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
		get() = recvVector.size
	val messageFromBuffer: Packet
		get() {
			if (recvVector.isEmpty()) throw EmptyStackException()
			return recvVector.removeAt(0)
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
		return recvVector.isNotEmpty()
	}

	/**
	 * Request resend.
	 *
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun requestResend() {
		sendInternalData(0.toByte(), ByteArray(0))
	}

	fun getAveragePingTime() = lastPings.average()

	@Throws(SocketException::class)
	fun ping(): Long {
		if (isClosed || !isConnected || !initialized) return 0
		synchronized(pingLock) {
			if (isClosed || !isConnected || !initialized) return 0
			val startTime = System.currentTimeMillis()
			try {
				sendData((-127).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				logger.finer(socketAlreadyClosed)
				Updater.remove("Ping" + hashCode())
			}
			while (pingReceived == -1L && System.currentTimeMillis() - startTime < 2000) {
				Thread.sleep(1)
			}
			if (System.currentTimeMillis() - startTime >= 2000) {
				try {
					sendClose()
				} catch (e: SocketException) {
					logger.finer(socketAlreadyClosed)
					Updater.remove("Ping" + hashCode())
				}
				close()
				return 2000
			}
			val roundTripTime = pingReceived - startTime
			pingReceived = -1L
			lastPings.add(roundTripTime)
			if (lastPings.size > 32) lastPings.removeAll(lastPings.subList(0, lastPings.size - 32).toSet())
			return roundTripTime
		}
	}

	@Throws(SocketException::class)
	fun internalPing(): Long {
		if (isClosed || !isConnected || !initialized) return 0
		synchronized(pingLock) {
			if (isClosed || !isConnected || !initialized) return 0
			val startTime = System.currentTimeMillis()
			try {
				pingReceived = -1L
				sendInternalData((-126).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				logger.finer(socketAlreadyClosed)
				Updater.remove("Ping" + hashCode())
			}
			while (pingReceived == -1L && System.currentTimeMillis() - startTime < 2000) {
				Thread.sleep(1)
			}
			if (System.currentTimeMillis() - startTime >= 2000) {
				try {
					sendClose()
				} catch (e: Exception) {
					logger.finer(socketAlreadyClosed)
					Updater.remove("Ping" + hashCode())
				}
				if (!isClosed)
					close()
				return 2000
			}
			val roundTripTime = pingReceived - startTime
			pingReceived = -1L
			lastPings.add(roundTripTime)
			if (lastPings.size > 32) lastPings.removeAll(lastPings.subList(0, lastPings.size - 32).toSet())
			return roundTripTime
		}
	}

	fun setupPeriodicInternalPing(millis: Long = 100) {
		Updater.add(Runnable {
			if (!isClosed) {
				if (pingReceived != -1L) {
					Thread {
						internalPing()
					}.start()
				}
			} else {
				disablePeriodicPing()
			}
		}, "Ping" + hashCode(), millis)
		Thread {
			internalPing()
		}.start()
	}

	fun setupPeriodicPing(millis: Long = 100) {
		Updater.add(Runnable {
			if (!isClosed) {
				if (pingReceived != -1L) {
					Thread {
						ping()
					}.start()
				}
			} else {
				disablePeriodicPing()
			}
		}, "Ping" + hashCode(), millis)
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
		synchronized(sendLock) {
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
		}
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
				val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
				if (handles[type]?.first == caller) {
					if (handle != null) {
						if (!handles.containsKey(type)) {
							val it = recvVector.iterator()
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

	override var shutdownHook: (DatatransferSocket) -> Unit
		get() = super.shutdownHook
		set(value) {
			super.shutdownHook = value
		}

	companion object {
		private const val basicSocket = "Basic Socket"

		private const val socketAlreadyClosed = "Socket already closed"

		private const val socketClosed = "Socket closed!"
	}

}
