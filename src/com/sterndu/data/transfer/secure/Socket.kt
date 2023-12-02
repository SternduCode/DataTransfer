@file:JvmName("Socket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.basic.Socket
import com.sterndu.encryption.Crypter
import com.sterndu.encryption.CrypterList.getByVersion
import com.sterndu.encryption.CrypterList.supportedVersions
import com.sterndu.encryption.DiffieHellman
import com.sterndu.multicore.LoggingUtil
import com.sterndu.multicore.Updater
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger
import javax.crypto.interfaces.DHPublicKey

open class Socket : Socket {

	private var logger: Logger = LoggingUtil.getLogger(SECURE_SOCKET)

	protected var dH: DiffieHellman? = null

	protected var crypter: Crypter? = null

	constructor()

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
				if (System.currentTimeMillis() - lastInitStageTime.get() > 5000) try {
					close()
					logger.log(Level.FINE, "$inetAddress tried to connect! But failed to initialize initCheck $appendix")
					removeUpdater("InitCheck $appendix")
					Updater.printAll(logger)
				} catch (e: IOException) {
					logger.log(Level.WARNING, SECURE_SOCKET, e)
				}
			}, "InitCheck $appendix")
			setHandle((-2).toByte()) { _: Byte, data: ByteArray ->
				try {
					lastInitStageTime.set(System.currentTimeMillis())
					var bb = ByteBuffer.wrap(data)
					val keyLength = bb.getInt()
					val keyData = ByteArray(keyLength)
					bb[keyData]
					val ib = bb.asIntBuffer()
					val li: MutableList<Int> = ArrayList()
					while (ib.hasRemaining()) li.add(ib.get())
					li.retainAll(supportedVersions.toSet())
					li.sort()
					lastInitStageTime.set(System.currentTimeMillis())
					dH!!.startHandshake()
					val kf = KeyFactory.getInstance("DiffieHellman")
					val key = kf.generatePublic(X509EncodedKeySpec(keyData)) as DHPublicKey
					dH!!.initialize(key.params)
					dH!!.doPhase(key, true)
					lastInitStageTime.set(System.currentTimeMillis())
					val pubKeyEnc = dH!!.publicKey.encoded
					bb = ByteBuffer.allocate(4 + pubKeyEnc.size)
					bb.putInt(li.last())
					bb.put(pubKeyEnc)
					sendInternalData((-3).toByte(), bb.array())
					crypter = getByVersion(li.last())!!
					crypter!!.makeKey(dH!!.getSecret()!!)
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
				}
			} // Test reduced number of calls && add hashing list avail stuff && add option to disable double hashing
			setHandle((-3).toByte()) { _: Byte, data: ByteArray ->
				try {
					lastInitStageTime.set(System.currentTimeMillis())
					val bb = ByteBuffer.wrap(data)
					crypter = getByVersion(bb.getInt())!!
					val keyData = ByteArray(data.size - 4)
					bb[keyData]
					val kf = KeyFactory.getInstance("DiffieHellman")
					val key = kf.generatePublic(X509EncodedKeySpec(keyData)) as DHPublicKey
					dH!!.doPhase(key, true)
					crypter!!.makeKey(dH!!.getSecret()!!)
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
			super.init(host)
			if (host) startHandshake()
		} catch (e: SocketException) {
			logger.log(Level.WARNING, SECURE_SOCKET, e)
		}
	}

	/**
	 * Start handshake.
	 *
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun startHandshake() {
		initialized = false
		dH!!.startHandshake()
		val pubKeyEnc = dH!!.publicKey.encoded
		val bb = ByteBuffer.allocate(4 + pubKeyEnc.size + 4 * supportedVersions.size)
		bb.putInt(pubKeyEnc.size)
		bb.put(pubKeyEnc)
		bb.asIntBuffer().put(supportedVersions)
		sendInternalData((-2).toByte(), bb.array())
	}

	companion object {
		private const val SECURE_SOCKET = "Secure Socket"
	}
}
