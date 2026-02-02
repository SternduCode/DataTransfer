@file:JvmName("ServerSocket")
package com.sterndu.data.transfer.basic

import java.io.IOException
import java.net.ServerSocket

open class ServerSocket(val serverSocket: ServerSocket = ServerSocket()) {

	@Throws(IOException::class)
	fun accept(): Socket {
		val s = try {
			serverSocket.accept()
		} catch (e: IOException) {
            throw e
		}
		return Socket(s, false).apply {
			initWithHost(true)
		}
	}
}
