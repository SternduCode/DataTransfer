@file:JvmName("ServerSocket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.Socket
import java.io.IOException
import java.net.ServerSocket

class ServerSocket(val serverSocket: ServerSocket = ServerSocket()) {

	@Throws(IOException::class)
	fun accept(): Socket {
		val s = try {
			serverSocket.accept()
		} catch (e: IOException) {
			throw e
		}
		return Socket(s, secureMode = true, host = true)
	}
}
