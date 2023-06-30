@file:JvmName("DatatransferServerSocket")
package com.sterndu.data.transfer

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

abstract class DatatransferServerSocket : ServerSocket {

	@Throws(IOException::class)
	constructor()
	@Throws(IOException::class)
	constructor(port: Int) : super(port)
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int) : super(port, backlog)
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int, bindAddr: InetAddress) : super(port, backlog, bindAddr)

	@Throws(IOException::class)
	abstract override fun accept(): Socket
}
