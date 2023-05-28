package com.sterndu.data.transfer;

import java.util.Arrays;
import java.util.Objects;

public final class Packet {
	private final byte type;
	private final byte[] data;

	public Packet(byte type, byte[] data) {
		this.type = type;
		this.data = data;
	}

	public byte type() {
		return type;
	}

	public byte[] data() {
		return data;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		Packet that = (Packet) obj;
		return this.type == that.type &&
				Arrays.equals(this.data, that.data);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, Arrays.hashCode(data));
	}

	@Override
	public String toString() {
		return "Packet[" +
				"type=" + type + ", " +
				"data=" + Arrays.toString(data) + ']';
	}

}
