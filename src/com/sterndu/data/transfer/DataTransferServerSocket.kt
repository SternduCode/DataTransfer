package com.sterndu.data.transfer

import java.io.IOException
import java.net.ServerSocket

open class DataTransferServerSocket(val serverSocket: ServerSocket = ServerSocket(), val secureMode: Boolean = false) {

	@Throws(IOException::class)
	fun accept(): DataTransferSocket {
		val s = try {
			serverSocket.accept()
		} catch (e: IOException) {
            throw e
		}
		return DataTransferSocket(s, secureMode, true)
	}
}