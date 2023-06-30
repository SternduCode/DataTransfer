@file:JvmName("Connector")
package com.sterndu.data.transfer

import com.sterndu.data.transfer.basic.Socket
import java.net.SocketException
import java.util.*
import java.util.function.BiConsumer


class Connector(
	val sock: Socket,
	val type: Byte
) {
	data class DataPlusType(val type: Byte, val data: ByteArray) {

		override fun equals(other: Any?): Boolean {
			if (other === this) return true
			if (other == null || other.javaClass != this.javaClass) return false
			val that = other as DataPlusType
			return type == that.type &&
					data.contentEquals(that.data)
		}

		override fun hashCode(): Int {
			return Objects.hash(type, data.contentHashCode())
		}

		override fun toString(): String {
			return "DataPlusType[" +
					"type=" + type + ", " +
					"data=" + data.contentToString() + ']'
		}
	}

	var handle: BiConsumer<Byte, ByteArray>? = null
		set(handle) {
			field = handle
			for (dpt in packets) handle?.accept(dpt.type, dpt.data)
		}

	private val packets: MutableList<DataPlusType>

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
		sock.setHandle(type) { _: Byte?, data: ByteArray ->
			val type = data[0]
			val dat = ByteArray(data.size - 1)
			System.arraycopy(data, 1, dat, 0, dat.size)
			if (handle == null) packets.add(DataPlusType(type, dat)) else handle!!.accept(type, dat)
		}
	}

	val packet: DataPlusType?
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
