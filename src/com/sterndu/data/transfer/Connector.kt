@file:JvmName("Connector")
package com.sterndu.data.transfer

import com.sterndu.data.transfer.basic.Socket
import java.net.SocketException
import java.util.*


class Connector(
	val sock: Socket,
	val type: Byte
) {

	var handle: ((Byte, ByteArray) -> Unit)? = null
		set(handle) {
			field = handle
			if (handle != null) for (dpt in packets) handle(dpt.type, dpt.data)
		}

	private val packets: MutableList<Packet>

	var handleDisabled = false
		private set

	init {
		packets = ArrayList()
		enableHandle()
	}

	/**
	 * Available packeets.
	 *
	 * @return the int
	 */
	fun availablePackets(): Int {
		return packets.size
	}

	/**
	 * Disable handle.
	 */
	fun disableHandle() {
		handleDisabled = true
		sock.setHandle(type, null)
	}

	/**
	 * Enable handle.
	 */
	fun enableHandle() {
		handleDisabled = false
		sock.setHandle(type) { _: Byte, data: ByteArray ->
			val type = data[0]
			val dat = ByteArray(data.size - 1)
			System.arraycopy(data, 1, dat, 0, dat.size)
			if (handle != null) handle?.let { it(type, dat) } else packets.add(Packet(type, dat))
		}
	}

	val packet: Packet?
		get() = if (availablePackets() > 0) packets.removeAt(0) else null

	@Throws(SocketException::class)
	fun requestResend() {
		sock.requestResend()
	}

	@Throws(SocketException::class)
	fun sendData(type: Byte, data: ByteArray) {
		val send = ByteArray(data.size + 1)
		send[0] = type
		System.arraycopy(data, 0, send, 1, data.size)
		sock.sendData(this.type, send)
	}

	/**
	 * Send data with type 1.
	 *
	 * @param data the data
	 *
	 * @throws SocketException the socket exception
	 */
	@Throws(SocketException::class)
	fun sendData(data: ByteArray) {
		sendData(1.toByte(), data)
	}
}
