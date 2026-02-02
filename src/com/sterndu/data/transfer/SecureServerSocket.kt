package com.sterndu.data.transfer

import java.io.IOException
import java.net.ServerSocket

class SecureServerSocket(val serverSocket: ServerSocket = ServerSocket()) {

	@Throws(IOException::class)
	fun accept(): DataTransferSocket {
		val s = try {
			serverSocket.accept()
		} catch (e: IOException) {
			throw e
		}
		return DataTransferSocket(s, secureMode = true, host = true)
	}
}