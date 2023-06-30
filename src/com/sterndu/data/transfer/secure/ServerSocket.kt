@file:JvmName("ServerSocket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.basic.ServerSocket
import java.io.IOException
import java.net.InetAddress

class ServerSocket : ServerSocket {
	@Throws(IOException::class)
	constructor()
	@Throws(IOException::class)
	constructor(port: Int) : super(port)
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int) : super(port, backlog)
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int, bindAddr: InetAddress?) : super(port, backlog, bindAddr)

	@Throws(IOException::class)
	override fun accept(): Socket {
		val s = Socket()
		super.implAccept(s)
		s.internalInit(true)
		return s
	}
}
