@file:JvmName("DatatransferSocket")
package com.sterndu.data.transfer

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.jvm.Throws

/**
 * The Class DatatransferSocket.
 */
abstract class DatatransferSocket : Socket {

	/** The initialized.  */
	var initialized = true
		protected set

	/** The shutdown hook.  */
	protected open var shutdownHook = Consumer { _: DatatransferSocket -> }

	/** The md.  */
	@JvmField
	protected var md: MessageDigest? = null

	/** The send lock.  */
	@JvmField
	protected var recLock = Any()
	@JvmField
	protected var sendLock = Any()

	/** The recv vector.  */
	@JvmField
	protected var recvVector = Vector<Packet>()

	/** The delayed send.  */
	@JvmField
	protected var delayedSend: MutableList<Packet> = ArrayList()

	/** The handles.  */
	@JvmField
	protected var handles: MutableMap<Byte, Pair<Class<*>, BiConsumer<Byte, ByteArray>>> = HashMap()

	/**
	 * Instantiates a new datatransfer socket.
	 */
	constructor()

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(IOException::class)
	constructor(address: InetAddress, port: Int) : super(address, port)

	/**
	 * Instantiates a new datatransfer socket.
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
	 * Instantiates a new datatransfer socket.
	 *
	 * @param proxy the proxy
	 */
	constructor(proxy: Proxy?) : super(proxy)

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @throws UnknownHostException the unknown host exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Throws(UnknownHostException::class, IOException::class)
	constructor(host: String, port: Int) : super(host, port)

	/**
	 * Instantiates a new datatransfer socket.
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
}
