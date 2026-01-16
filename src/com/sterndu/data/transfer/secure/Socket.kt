@file:JvmName("Socket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.basic.Socket
import com.sterndu.encryption.Crypter
import com.sterndu.encryption.CrypterProvider
import com.sterndu.encryption.DiffieHellman
import com.sterndu.multicore.LoggingUtil
import com.sterndu.multicore.Updater
import java.io.IOException
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger
import java.net.Socket as NetSocket

open class Socket : Socket {

	private var logger: Logger = LoggingUtil.getLogger(SECURE_SOCKET)

	protected lateinit var dH: DiffieHellman

	protected var crypter: Crypter? = null

	constructor()

	constructor(socket: NetSocket) : super(socket)

	@Throws(IOException::class)
	constructor(address: InetAddress, port: Int) : super(address, port)

	@Throws(IOException::class)
	constructor(address: InetAddress, port: Int, localAddr: InetAddress, localPort: Int) : super(
		address,
		port,
		localAddr,
		localPort
	)

	@Throws(IOException::class, UnknownHostException::class)
	constructor(host: String, port: Int) : super(host, port)

	@Throws(IOException::class)
	constructor(host: String, port: Int, localAddr: InetAddress, localPort: Int) : super(
		host,
		port,
		localAddr,
		localPort
	)

	/**
	 * Receive data implementation, handles transformations of data after it is received.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	override fun implReceiveData(type: Byte, data: ByteArray): ByteArray {
		if (!initialized) throw IllegalStateException("Socket not initialized!")
		return when (type) {
			0.toByte(), (-1).toByte(), (-2).toByte(), (-3).toByte(), (-4).toByte(), (-5).toByte(), (-126).toByte() -> data
			else -> crypter!!.decrypt(data)
		}
	}

	/**
	 * Send data implementation, handles transformations of data before it is sent
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	override fun implSendData(type: Byte, data: ByteArray): ByteArray {
		if (!initialized) throw IllegalStateException("Socket not initialized!")
		return when (type) {
			0.toByte(), (-1).toByte(), (-2).toByte(), (-3).toByte(), (-4).toByte(), (-5).toByte(), (-126).toByte() -> data
			else -> crypter!!.encrypt(data)
		}
	}

	private fun removeUpdater(key: String) {
		Updater.remove(key)
	}

	/**
	 * @param host the host
	 */
	override fun init(host: Boolean) {
		if (logger == null)
			logger = LoggingUtil.getLogger(SECURE_SOCKET)
		try {
			isHost = host
			initialized = false
			dH = DiffieHellman()
			val lastInitStageTime = AtomicLong(System.currentTimeMillis())
			Updater.add(Runnable {
				if (System.currentTimeMillis() - lastInitStageTime.get() > 15000) try {
					close()
					logger.log(Level.FINE, "${socket.inetAddress} tried to connect! But failed to initialize initCheck $appendix")
					removeUpdater("InitCheck $appendix")
					Updater.printAll(logger)
				} catch (e: IOException) {
					logger.log(Level.WARNING, SECURE_SOCKET, e)
				}
			}, "InitCheck $appendix")
			setHandle((-2).toByte()) { _: Byte, data: ByteArray ->
				initPhase1(data, lastInitStageTime)
			} // Test reduced number of calls && add hashing list avail stuff && add option to disable double hashing
			setHandle((-3).toByte()) { _: Byte, data: ByteArray ->
				initPhase2(data, lastInitStageTime)
			}
			super.init(host)
			if (host) startHandshake()
		} catch (e: SocketException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		}
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
			removeUpdater("InitCheck $appendix")
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: InvalidKeySpecException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: InvalidAlgorithmParameterException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: SocketException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: InvalidKeyException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: Exception) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
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
			removeUpdater("InitCheck $appendix")
		} catch (e: NoSuchAlgorithmException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: InvalidKeySpecException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		} catch (e: InvalidKeyException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		}
	}

	companion object {
		private const val SECURE_SOCKET = "Secure Socket"
	}
}
