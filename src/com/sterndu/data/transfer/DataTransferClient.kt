@file:JvmName("DataTransferClient")
package com.sterndu.data.transfer

import com.sterndu.encryption.Crypter
import com.sterndu.encryption.CrypterProvider
import com.sterndu.encryption.DiffieHellman
import com.sterndu.multicore.LoggingUtil
import com.sterndu.multicore.Updater
import io.ktor.utils.io.core.Closeable
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.EmptyStackException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.StampedLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.ArrayDeque
import kotlin.text.toByteArray

abstract class DataTransferClient(val secureMode: Boolean = true): Closeable {

	private var logger: Logger = LoggingUtil.getLogger(DATA_TRANSFER_CLIENT)

	protected lateinit var dH: DiffieHellman

	protected var crypter: Crypter? = null

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
		return if (!secureMode) data
		else {
			when (type) {
				0.toByte(), (-1).toByte(), (-2).toByte(), (-3).toByte(), (-4).toByte(), (-5).toByte(), (-126).toByte() -> data
				else -> {
					val crypter = crypter
					check(initialized && crypter != null) { "Socket not initialized!" }
					crypter.decrypt(data)
				}
			}
		}
	}

	/**
	 * Impl send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	protected open fun implSendData(type: Byte, data: ByteArray): ByteArray {
		return if (!secureMode) data
		else {
			val crypter = crypter
			check(initialized && crypter != null) { "Socket not initialized!" }
			when (type) {
				0.toByte(), (-1).toByte(), (-2).toByte(), (-3).toByte(), (-4).toByte(), (-5).toByte(), (-126).toByte() -> data
				else -> crypter.encrypt(data)
			}
		}
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

	protected open fun init(host: Boolean) {
		if (logger == null) {
			logger = LoggingUtil.getLogger(DATA_TRANSFER_CLIENT)
			logger.info("FFS needed late init logger")
		}
		try {
			isHost = host
			md = MessageDigest.getInstance("SHA-512/256") // Better performance than SHA-256
			if (secureMode) {
				initialized = false
				dH = DiffieHellman()
			}
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		}
		val lastInitStageTime = AtomicLong(System.currentTimeMillis())
		setDefaultHandles(lastInitStageTime)
		setDefaultUpdaterTasks(lastInitStageTime)

        if (secureMode) {
            try {
                if (host) startHandshake()
            } catch (e: SocketException) {
                logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
            }
        }

    }

	private fun setDefaultHandles(lastInitStageTime: AtomicLong) {
		setHandle((-1).toByte()) { _: Byte, _: ByteArray ->
			try {
				if (!isClosed) {
					logger.fine("close recv $this")
					close()
				}
			} catch (e: IOException) {
				logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
			}
		}

		if (secureMode) {
			setHandle((-2).toByte()) { _: Byte, data: ByteArray ->
				initPhase1(data, lastInitStageTime)
			} // Test reduced number of calls && add hashing list avail stuff && add option to disable double hashing
			setHandle((-3).toByte()) { _: Byte, data: ByteArray ->
				initPhase2(data, lastInitStageTime)
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
	}

	private fun setDefaultUpdaterTasks(lastInitStageTime: AtomicLong) {
		Updater.add("CheckForMsgs $appendix") {
			if (!isClosed && isDataAvailable) try {
				val data = receiveData()
				Files.write(File("./${appendix}_${System.currentTimeMillis()}_${data.type}.pckt").toPath(), data.data, StandardOpenOption.CREATE, StandardOpenOption.WRITE) //write content -> appendix_timestamp.pckt
				getHandle(data.type)?.also {
					it(data.type, data.data)
				} ?: receiveQueue.add(data)
			} catch (e: IOException) {
				logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
			}
			if (delayedSend.isNotEmpty() && initialized && !isClosed) {
				val (type, data) = delayedSend.removeAt(0)
				sendData(type, data)
			}
		}
		Updater.add("PingKill $appendix") {
			if (!isClosed && pingStartTime != 0L && System.currentTimeMillis() - pingStartTime >= 5000) {
				try {
					sendClose()
				} catch (_: SocketException) {
					logger.finer(ALREADY_CLOSED)
					disablePeriodicPing()
				}
				close()
			}
		}
		if (secureMode) {
			Updater.add("InitCheck $appendix") {
				if (System.currentTimeMillis() - lastInitStageTime.get() > 15000) try {
					close()
					logger.log(Level.FINE, "${name()} tried to connect! But failed to initialize initCheck $appendix")
					Updater.remove("InitCheck $appendix")
					Updater.printAll(logger)
				} catch (e: IOException) {
					logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
				}
			}
		}
	}

	protected fun removeDefaultUpdaterTasks() {
		Updater.remove("CheckForMsgs $appendix")
		Updater.remove("PingKill $appendix")
	}

	@Throws(SocketException::class)
	fun startHandshake() {
		initialized = false
		dH.startHandshake()
		val pubKeyEnc = dH.publicKey?.encoded ?: throw Exception("Initialization failed")
		val bb = ByteBuffer.allocate(4 + pubKeyEnc.size + 2 * CrypterProvider.availableCrypterCodes.size)
		bb.putInt(pubKeyEnc.size)
		bb.put(pubKeyEnc)
		bb.asShortBuffer().put(CrypterProvider.availableCrypterCodes)
		sendRawData((-2).toByte(), bb.array())
	}

	private fun initPhase1(data: ByteArray, lastInitStageTime: AtomicLong) {
		try {
			lastInitStageTime.set(System.currentTimeMillis())
			var bb = ByteBuffer.wrap(data)
			val keyLength = bb.getInt()
			val keyData = ByteArray(keyLength)
			bb[keyData]
			val shortBuffer = bb.asShortBuffer()
			val li: MutableList<Short> = ArrayList()
			while (shortBuffer.hasRemaining()) li.add(shortBuffer.get())
			li.retainAll(CrypterProvider.availableCrypterCodes.toSet())
			li.sort()
			lastInitStageTime.set(System.currentTimeMillis())
			if (System.getProperty("debug") == "true") println("FFS2")
			val kf = KeyFactory.getInstance("X25519")
			val key = kf.generatePublic(X509EncodedKeySpec(keyData))
			if (System.getProperty("debug") == "true") println("FFS3 $key")
			dH.startHandshake()
			if (System.getProperty("debug") == "true") println("FFS4")
			dH.doPhase(key, true)
			if (System.getProperty("debug") == "true") println("FFS5")
			lastInitStageTime.set(System.currentTimeMillis())
			val pubKeyEnc = dH.publicKey?.encoded ?: throw Exception("KeyExchange is not fully completed! No PublicKey available")
			if (System.getProperty("debug") == "true") println("FFS6")
			bb = ByteBuffer.allocate(2 + pubKeyEnc.size)
			bb.putShort(li.last())
			bb.put(pubKeyEnc)
			sendRawData((-3).toByte(), bb.array())
			crypter = CrypterProvider.getCrypterByCode(li.last())!!
			crypter!!.makeKey(dH.getSecret()!!)
			initialized = true
			Updater.remove("InitCheck $appendix")
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: InvalidKeySpecException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: InvalidAlgorithmParameterException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: SocketException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: InvalidKeyException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: Exception) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		}
	}

	private fun initPhase2(data: ByteArray, lastInitStageTime: AtomicLong) {
		try {
			lastInitStageTime.set(System.currentTimeMillis())
			val bb = ByteBuffer.wrap(data)
			crypter = CrypterProvider.getCrypterByCode(bb.getShort())!!
			val keyData = ByteArray(data.size - 2)
			bb[keyData]
			val kf = KeyFactory.getInstance("X25519")
			val key = kf.generatePublic(X509EncodedKeySpec(keyData)) as PublicKey
			dH.doPhase(key, true)
			crypter!!.makeKey(dH.getSecret()!!)
			initialized = true
			Updater.remove("InitCheck $appendix")
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: InvalidKeySpecException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		} catch (e: InvalidKeyException) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
		}
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
	val hasMessage: Boolean get() = receiveQueue.isNotEmpty()

	fun getAveragePingTime() = lastPings.average()

	val lastPingTime: Long get() = lastPings.lastOrNull() ?: -1L

	@Throws(SocketException::class)
	fun ping() {
		if (isClosed || !isConnected || !initialized) return
		synchronized(pingLock) {
			if (isClosed || !isConnected || !initialized) return
			pingStartTime = System.currentTimeMillis()
			try {
				sendData((-127).toByte(), "Ping".toByteArray(Charsets.UTF_8))
			} catch (_: Exception) {
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
			} catch (_: Exception) {
				logger.finer(ALREADY_CLOSED)
				Updater.remove("Ping $appendix")
			}
		}
	}

	fun setupPeriodicRawPing(millis: Long = 100) {
		Updater.add("Ping $appendix", millis) {
			if (!isClosed) {
				if (pingStartTime == 0L) {
					rawPing() // Potential deadlock
				}
			} else {
				disablePeriodicPing()
			}
		}
		rawPing() // Potential deadlock
	}

	fun setupPeriodicPing(millis: Long = 100) {
		Updater.add("Ping $appendix", millis) {
			if (!isClosed) {
				if (pingStartTime == 0L) {
					ping() // Potential deadlock
				}
			} else {
				disablePeriodicPing()
			}
		}
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
							receiveQueue.removeIf { (type1, data) ->
								if (type1 == type) {
									handle(type1, data)
									true
								} else false
							}
						}
						handles[type] = caller to handle
					} else handles.remove(type)
					return true
				}
				false
			}
		} catch (e: Exception) {
			logger.log(Level.WARNING, DATA_TRANSFER_CLIENT, e)
			false
		}
	}

	companion object {
		private const val ALREADY_CLOSED = "DataTransferClient Already closed"

		private const val DATA_TRANSFER_CLIENT = "Data Transfer Client"
	}

}
