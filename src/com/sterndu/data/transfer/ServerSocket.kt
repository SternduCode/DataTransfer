package com.sterndu.data.transfer

import java.io.IOException
import java.net.ServerSocket

open class ServerSocket(val serverSocket: ServerSocket = ServerSocket(), val secureMode: Boolean = false) {

	@Throws(IOException::class)
	fun accept(): Socket {
		val s = try {
			serverSocket.accept()
		} catch (e: IOException) {
            throw e
		}
		return Socket(s, secureMode, true)
	}
}