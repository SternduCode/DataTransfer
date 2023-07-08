@file:JvmName("Socket")
package com.sterndu.data.transfer.basic

import com.sterndu.data.transfer.DatatransferSocket
import com.sterndu.data.transfer.Packet
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
import kotlin.collections.ArrayList

open class Socket : DatatransferSocket {

	/** If this Socket is in Host mode.  */
	open var isHost = false
		protected set

	/**
	 * Instantiates a new socket.
	 *
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
	constructor(address: InetAddress, port: Int) : super(address, port) {
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
					close()
				} catch (e: IOException) {
					e.printStackTrace()
				}
			}
		} catch (e: NoSuchAlgorithmException) {
			e.printStackTrace()
		}
		Updater.add(ThrowingRunnable {
			if (!isClosed && inputStream.available() > 0) try {
				val data = receiveData()
				if (handles.containsKey(data.type)) getHandle(data.type)!!(data.type, data.data) else recvVector.add(data)
			} catch (e: IOException) {
				e.printStackTrace()
			}
			if (delayedSend.isNotEmpty() && initialized) {
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
			if (readXBytes(b, inputStream, b.size)) {
				val type = b[0]
				val length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1)
				b = ByteArray(32)
				var data = ByteArray(length)
				if (readXBytes(data, inputStream, length)
					&& readXBytes(b, inputStream, b.size) && Arrays.equals(b, md!!.digest(data))
				) {
					packet = Packet(type, data)
					if ("true" == System.getProperty("debug")) {
						if (data.size > 5000) data = data.copyOfRange(0, 5000)
						System.err.println(
							type.toString() + "r[length:" + length + ",data:" + data.contentToString() + ",hash:"
									+ b.contentToString()
						)
					}
				} else {
					if ("true" == System.getProperty("debug")) {
						if (data.size > 5000) data = data.copyOfRange(0, 5000)
						System.err.println(
							type.toString() + "f[length:" + length + ",data:" + data.contentToString() + ",hash:"
									+ b.contentToString()
						)
					}
					return Packet(((-128).toByte()), ByteArray(0))
				}
			} else {
				if ("true" == System.getProperty("debug")) System.err.println("f" + b.contentToString())
				return Packet(((-128).toByte()), ByteArray(0))
			}
		}
		return Packet(packet.type, implReceiveData(packet.type, packet.data))
		// type byte; length int; data byte[]; hash byte[32];
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
			val modifiedData = implSendData(type, data)
			val hash = md!!.digest(modifiedData)
			val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(modifiedData.size).array()
			try {
				val os = outputStream
				os.write(type.toInt())
				os.write(lengthBytes)
				os.write(modifiedData)
				os.write(hash)
				if ("true" == System.getProperty("debug", "false")) System.err.println(
					type.toString() + "s[length_bytes:" + Arrays.toString(lengthBytes) + ", length:"
							+ modifiedData.size
							+ ",data:" + modifiedData.contentToString() + ",hash:" + Arrays.toString(hash)
				)
			} catch (e: SocketException) {
				throw e
			} catch (e: IOException) {
				e.printStackTrace()
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
		try {
			synchronized(recLock) {
				synchronized(sendLock) {
					try {
						if (!isClosed) {
							shutdownHook(this)
							shutdownOutput()
							shutdownInput()
							Updater.remove("CheckForMsgs" + hashCode())
							Updater.remove("Ping" + hashCode())
							super.close()
						}
					} catch (e: SocketException) {
						super.close()
					}
				}
			}
		} catch (e: NullPointerException) {
			getInstance().remove("CheckForMsgs" + hashCode())
			super.close()
		}
	}

	/**
	 * Gets the handle.
	 *
	 * @param type the type
	 * @return the handle
	 */
	fun getHandle(type: Byte): ((Byte, ByteArray) -> Unit)? {
		return if (hasHandle(type)) handles[type]!!.second else null
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
				if ("true" == System.getProperty("debug", "false")) {
					System.err.println(
						type.toString() + "s[length_bytes:" + Arrays.toString(lengthBytes) + ", length:"
								+ modifiedData.size
								+ ",data:" + modifiedData.contentToString() + ",hash:" + Arrays.toString(hash)
					)
				} else Unit
			} catch (e: SocketException) {
				try {
					close()
				} catch (ex: IOException) {
					ex.initCause(e)
					ex.printStackTrace()
				}
			} catch (e: IOException) {
				e.printStackTrace()
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
			val caller = Class.forName(Thread.currentThread().stackTrace[2].className)
			if (!handles.containsKey(type) || handles[type]!!.first == caller) {
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
		} catch (e: Exception) {
			e.printStackTrace()
			false
		}
	}

	override var shutdownHook: (DatatransferSocket) -> Unit
		get() = super.shutdownHook
		set(value) {
			super.shutdownHook = value
		}

	companion object {
		private const val socketClosed = "Socket closed!"
	}
}
