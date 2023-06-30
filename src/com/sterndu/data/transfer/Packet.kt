@file:JvmName("Packet")
package com.sterndu.data.transfer

import java.util.*

data class Packet(val type: Byte, val data: ByteArray) {

	override fun equals(other: Any?): Boolean {
		if (other === this) return true
		if (other == null || other.javaClass != this.javaClass) return false
		val that = other as Packet
		return type == that.type &&
				data.contentEquals(that.data)
	}

	override fun hashCode(): Int {
		return Objects.hash(type, data.contentHashCode())
	}

	override fun toString(): String {
		return "Packet[" +
				"type=" + type + ", " +
				"data=" + data.contentToString() + ']'
	}
}
