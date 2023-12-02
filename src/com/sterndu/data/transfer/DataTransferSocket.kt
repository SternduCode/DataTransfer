@file:JvmName("DataTransferSocket")
package com.sterndu.data.transfer

import java.io.IOException
import java.net.*
import java.security.MessageDigest
import java.util.*
import kotlin.collections.ArrayDeque

abstract class DataTransferSocket : Socket {

	var initialized = true
		protected set

	protected open var shutdownHook = { _: DataTransferSocket -> }

	@JvmField
	protected var md: MessageDigest? = null

	@JvmField
	protected var recLock = Any()
	@JvmField
	protected var sendLock = Any()

	@JvmField
	protected var receiveQueue = ArrayDeque<Packet>()

	@JvmField
	protected var delayedSend: MutableList<Packet> = ArrayList()

	@JvmField
	protected var handles: MutableMap<Byte, Pair<Class<*>, (Byte, ByteArray) -> Unit>> = HashMap()

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

	constructor(proxy: Proxy?) : super(proxy)

	@Throws(UnknownHostException::class, IOException::class)
	constructor(host: String, port: Int) : super(host, port)

	@Throws(IOException::class)
	constructor(host: String, port: Int, localAddr: InetAddress, localPort: Int) : super(
		host,
		port,
		localAddr,
		localPort
	)
}
