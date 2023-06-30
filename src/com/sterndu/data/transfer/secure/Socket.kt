@file:JvmName("Socket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.basic.Socket
import com.sterndu.encryption.Crypter
import com.sterndu.encryption.CrypterList.getByVersion
import com.sterndu.encryption.CrypterList.supportedVersions
import com.sterndu.encryption.DiffieHellman
import com.sterndu.multicore.Updater.Companion.getInstance
import java.io.IOException
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.interfaces.DHPublicKey

open class Socket : Socket {
	/**
	 * Gets the dh.
	 *
	 * @return the dh
	 */
	/** The dh.  */
	var dH: DiffieHellman? = null
		protected set

	/** The crypter.  */
	protected var crypter: Crypter? = null

	/**
	 * Instantiates a new socket.
	 */
	constructor()

	/**
	 * Instantiates a new socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	constructor(address: InetAddress, port: Int) : super(address, port)

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
	)

	/**
	 * Instantiates a new socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @throws UnknownHostException the unknown host exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class, UnknownHostException::class)
	constructor(host: String, port: Int) : super(host, port)

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
	)

	/**
	 * Impl recieve data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	override fun implReceiveData(type: Byte, data: ByteArray): ByteArray {
		return when (type) {
			0.toByte(), (-1).toByte(), (-2).toByte(), (-3).toByte(), (-4).toByte(), (-5).toByte() -> data
			else -> crypter!!.decrypt(data)
		}
	}

	/**
	 * Impl send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	override fun implSendData(type: Byte, data: ByteArray): ByteArray {
		return when (type) {
			0.toByte(), (-1).toByte(), (-2).toByte(), (-3).toByte(), (-4).toByte(), (-5).toByte() -> data
			else -> crypter!!.encrypt(data)
		}
	}

	/**
	 * Inits the.
	 *
	 * @param host the host
	 */
	override fun init(host: Boolean) {
		try {
			isHost = host
			initialized = false
			dH = DiffieHellman()
			val lastInitStageTime = AtomicLong(System.currentTimeMillis())
			getInstance().add(Runnable {
				if (System.currentTimeMillis() - lastInitStageTime.get() > 2000) try {
					close()
					getInstance().remove("InitCheck" + hashCode())
				} catch (e: IOException) {
					e.printStackTrace()
				}
			}, "InitCheck" + hashCode())
			setHandle((-2).toByte()) { type: Byte, data: ByteArray ->
				try {
					var bb = ByteBuffer.wrap(data)
					val keyLength = bb.getInt()
					val keyData = ByteArray(keyLength)
					bb[keyData]
					val ib = bb.asIntBuffer()
					val li: MutableList<Int> = ArrayList()
					while (ib.hasRemaining()) li.add(ib.get())
					li.retainAll(supportedVersions.toSet())
					li.sort()
					initialized = false
					dH!!.startHandshake()
					val kf = KeyFactory.getInstance("DiffieHellman")
					val key = kf.generatePublic(X509EncodedKeySpec(keyData, "DiffieHellman")) as DHPublicKey
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
					getInstance().remove("InitCheck" + hashCode())
				} catch (e: NoSuchAlgorithmException) {
					e.printStackTrace()
				} catch (e: InvalidKeySpecException) {
					e.printStackTrace()
				} catch (e: InvalidAlgorithmParameterException) {
					e.printStackTrace()
				} catch (e: SocketException) {
					e.printStackTrace()
				} catch (e: InvalidKeyException) {
					e.printStackTrace()
				}
			} // Test reduced number of calls && add hashing list avail stuff && add option to disable double hashing
			setHandle((-3).toByte()) { type: Byte, data: ByteArray ->
				try {
					lastInitStageTime.set(System.currentTimeMillis())
					val bb = ByteBuffer.wrap(data)
					crypter = getByVersion(bb.getInt())!!
					val keyData = ByteArray(data.size - 4)
					bb[keyData]
					val kf = KeyFactory.getInstance("DiffieHellman")
					val key = kf.generatePublic(X509EncodedKeySpec(keyData, "DiffieHellman")) as DHPublicKey
					dH!!.doPhase(key, true)
					crypter!!.makeKey(dH!!.getSecret()!!)
					initialized = true
					getInstance().remove("InitCheck" + hashCode())
				} catch (e: NoSuchAlgorithmException) {
					e.printStackTrace()
				} catch (e: InvalidKeySpecException) {
					e.printStackTrace()
				} catch (e: InvalidKeyException) {
					e.printStackTrace()
				}
			}
			super.init(host)
			if (host) startHandshake()
		} catch (e1: SocketException) {
			e1.printStackTrace()
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
}
