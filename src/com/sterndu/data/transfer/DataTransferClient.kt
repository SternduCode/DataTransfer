@file:JvmName("DataTransferClient")
package com.sterndu.data.transfer

import com.sterndu.multicore.LoggingUtil
import com.sterndu.multicore.Updater
import io.ktor.utils.io.core.Closeable
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.EmptyStackException
import java.util.concurrent.locks.StampedLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.ArrayDeque
import kotlin.text.toByteArray

abstract class DataTransferClient: Closeable {

	private var logger: Logger = LoggingUtil.getLogger(dataTransferClient)

	var initialized = true
		protected set

	protected open var shutdownHook = { _: DataTransferClient -> }

	@JvmField
	protected var md: MessageDigest? = null

	@JvmField
	protected var recvLock = StampedLock()
	@JvmField
	protected var sendLock = StampedLock()
	@JvmField
	protected var receiveQueue = ArrayDeque<Packet>()

	@JvmField
	protected var delayedSend: MutableList<Packet> = ArrayList()

	@JvmField
	protected var handles: MutableMap<Byte, Pair<Class<*>, (Byte, ByteArray) -> Unit>> = HashMap()

	protected abstract var appendix: String

	private var packetCounterSend: Short = 0
	private var packetCounterReceive: Short = 0
	private val resendList = HashMap<Short, Packet>()

	open var isHost = false
		protected set

	private val pingLock = Any()

	private val lastPings = ArrayList<Long>()

	private var pingStartTime = 0L

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

	abstract val isClosed: Boolean

	abstract val isDataAvailable: Boolean

	abstract val isConnected: Boolean

	abstract override fun close()

	internal open fun initWithHost(host: Boolean) {
		init(host)
	}

	protected open fun init(host: Boolean){
		if (logger == null)
			logger = LoggingUtil.getLogger(dataTransferClient)
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
					logger.log(Level.WARNING, dataTransferClient, e)
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
						sendRawData((-126).toByte(), "Pong".toByteArray(Charsets.UTF_8))
					else pingReceived()
				}
			}
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, dataTransferClient, e)
		}
		Updater.add({
			if (!isClosed && isDataAvailable) try {
				val data = receiveData()
				Files.write(File("./${appendix}_${System.currentTimeMillis()}_${data.type}.pckt").toPath(), data.data, StandardOpenOption.CREATE, StandardOpenOption.WRITE) //write content -> appendix_timestamp.pckt
				if (handles.containsKey(data.type)) getHandle(data.type)!!(data.type, data.data) else receiveQueue.add(data)
			} catch (e: IOException) {
				logger.log(Level.WARNING, dataTransferClient, e)
			}
			if (delayedSend.isNotEmpty() && initialized && !isClosed) {
				val (type, data) = delayedSend.removeAt(0)
				sendData(type, data)
			}
		}, "CheckForMsgs $appendix")
		Updater.add({
			if (!isClosed && pingStartTime != 0L && System.currentTimeMillis() - pingStartTime >= 5000) {
				try {
					sendClose()
				} catch (e: SocketException) {
					logger.finer(ALREADY_CLOSED)
					disablePeriodicPing()
				}
				close()
			}
		}, "PingKill $appendix")
	}

	abstract fun name(withClassName: Boolean = false): String

	protected abstract fun receiveData(): Packet

	protected fun sendRawData(type: Byte, data: ByteArray) {
		sendData(type, data, true)
	}

	abstract fun sendData(type: Byte, data: ByteArray, raw: Boolean = false)

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
		sendRawData(0.toByte(), ByteArray(0))
	}

	fun getAveragePingTime() = lastPings.average()

	fun getLastPingTime() = lastPings.lastOrNull() ?: -1L

	@Throws(SocketException::class)
	fun ping() {
		if (isClosed || !isConnected || !initialized) return
		synchronized(pingLock) {
			if (isClosed || !isConnected || !initialized) return
			pingStartTime = System.currentTimeMillis()
			try {
				sendData((-127).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				logger.finer(ALREADY_CLOSED)
				Updater.remove("Ping $appendix")
			}
		}
	}

	@Throws(SocketException::class)
	fun rawPing() {
		if (isClosed || !isConnected || !initialized) return
		synchronized(pingLock)  {
			if (isClosed || !isConnected || !initialized) return
			pingStartTime = System.currentTimeMillis()
			try {
				sendRawData((-126).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (e: Exception) {
				logger.finer(ALREADY_CLOSED)
				Updater.remove("Ping $appendix")
			}
		}
	}

	fun setupPeriodicRawPing(millis: Long = 100) {
		Updater.add(Runnable {
			if (!isClosed) {
				if (pingStartTime == 0L) {
					rawPing() // Potential deadlock
				}
			} else {
				disablePeriodicPing()
			}
		}, "Ping $appendix", millis)
		rawPing() // Potential deadlock
	}

	fun setupPeriodicPing(millis: Long = 100) {
		Updater.add(Runnable {
			if (!isClosed) {
				if (pingStartTime == 0L) {
					ping() // Potential deadlock
				}
			} else {
				disablePeriodicPing()
			}
		}, "Ping $appendix", millis)
		ping() // Potential deadlock
	}

	/**
	 * Send close.
	 *
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun sendClose() {
		logger.log(Level.WARNING, "send close $appendix $this", Exception("send close"))
		if (!isClosed && isConnected) sendRawData((-1).toByte(), ByteArray(0))
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
			logger.log(Level.WARNING, dataTransferClient, e)
			false
		}
	}

	companion object {
		private const val ALREADY_CLOSED = "DataTransferClient Already closed"

		private const val dataTransferClient = "Data Transfer Client"
	}

}
