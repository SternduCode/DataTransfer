@file:JvmName("Connector")
package com.sterndu.data.transfer

import java.net.SocketException


class Connector(
	val client: DataTransferClient,
	val type: Byte
) {

	var handle: ((Byte, ByteArray) -> Unit)? = null
		set(handle) {
			field = handle
			if (handle != null) for (dpt in packets) handle(dpt.type, dpt.data)
		}

	private val packets: MutableList<Packet> = ArrayList()

    var handleDisabled = false
		private set

	init {
        enableHandle()
	}

	/**
	 * Available packets.
	 *
	 * @return the int
	 */
	fun availablePackets(): Int {
		return packets.size
	}

	fun disableHandle() {
		handleDisabled = true
		client.setHandle(type, null)
	}

	fun enableHandle() {
		handleDisabled = false
		client.setHandle(type) { _: Byte, data: ByteArray ->
			val type = data[0]
			val dat = ByteArray(data.size - 1)
			System.arraycopy(data, 1, dat, 0, dat.size)
			if (handle != null) handle?.let { it(type, dat) } else packets.add(Packet(type, dat))
		}
	}

	val packet: Packet?
		get() = if (availablePackets() > 0) packets.removeAt(0) else null

	@Throws(SocketException::class)
	fun sendData(type: Byte, data: ByteArray) {
		val send = ByteArray(data.size + 1)
		send[0] = type
		System.arraycopy(data, 0, send, 1, data.size)
		client.sendData(this.type, send)
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
