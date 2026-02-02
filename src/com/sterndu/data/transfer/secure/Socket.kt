@file:JvmName("Socket")
package com.sterndu.data.transfer.secure

import com.sterndu.data.transfer.basic.Socket
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.Socket as NetSocket

@Deprecated("Use com.sterndu.data.transfer.basic.Socket instead with secureMode = true", ReplaceWith("Socket", "import com.sterndu.data.transfer.basic.Socket"))
open class Socket : Socket {

	constructor()

	constructor(socket: NetSocket) : super(socket, true)

	@Throws(IOException::class)
	@Deprecated("Use Socket(socket: NetSocket) instead", ReplaceWith("Socket(NetSocket(address, port))", "import java.net.Socket as NetSocket"))
	constructor(address: InetAddress, port: Int) : super(address, port, true)

	@Throws(IOException::class)
	@Deprecated("Use Socket(socket: NetSocket) instead", ReplaceWith("Socket(NetSocket(address, port, localAddr, localPort))", "import java.net.Socket as NetSocket"))
	constructor(address: InetAddress, port: Int, localAddr: InetAddress, localPort: Int) : super(
		address,
		port,
		localAddr,
		localPort,
		true
	)

	@Throws(IOException::class, UnknownHostException::class)
	@Deprecated("Use Socket(socket: NetSocket) instead", ReplaceWith("Socket(NetSocket(host, port))", "import java.net.Socket as NetSocket"))
	constructor(host: String, port: Int) : super(host, port, true)

	@Throws(IOException::class)
	@Deprecated("Use Socket(socket: NetSocket) instead", ReplaceWith("Socket(NetSocket(host, port, localAddr, localPort))", "import java.net.Socket as NetSocket"))
	constructor(host: String, port: Int, localAddr: InetAddress, localPort: Int) : super(
		host,
		port,
		localAddr,
		localPort,
		true
	)
}
