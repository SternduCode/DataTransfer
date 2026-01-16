@file:JvmName("ServerSocket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.DataTransferClient
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class ServerSocket(val serverSocket: ServerSocket = ServerSocket()) {
	@Throws(IOException::class)
	constructor(port: Int) : this(ServerSocket(port))
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int) : this(ServerSocket(port, backlog))
	@Throws(IOException::class)
	constructor(port: Int, backlog: Int, bindAddr: InetAddress?) : this(ServerSocket(port, backlog, bindAddr))

	@Throws(IOException::class)
	fun accept(): DataTransferClient {
		val s = try {
			serverSocket.accept()
		} catch (e: IOException) {
			throw e
		}
		return Socket(s).apply {
			initWithHost(true)
		}
	}
}
