@file:JvmName("ServerSocket")
package com.sterndu.data.transfer.basic

import com.sterndu.data.transfer.DataTransferServerSocket
import java.io.IOException
import java.net.InetAddress

open class ServerSocket : DataTransferServerSocket {
	@Throws(IOException::class)
	constructor()
	@Throws(IOException::class)
	constructor(port: Int) : super(port)
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int) : super(port, backlog)
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int, bindAddr: InetAddress?) : super(port, backlog, bindAddr!!)

	@Throws(IOException::class)
	override fun accept(): Socket {
		val s = Socket()
		try {
			super.implAccept(s)
		} catch (e: IOException) {
			if (s.isBound && !s.isClosed) {
				s.close()
			}
			Socket.allSockets.remove(s)
            throw e
		}
		s.internalInit(true)
		return s
	}
}
