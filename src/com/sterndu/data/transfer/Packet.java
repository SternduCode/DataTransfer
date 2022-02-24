package com.sterndu.data.transfer;

public record Packet(byte type, byte[] data) {

	public Packet(byte type, byte[] data) {
		this.type = type;
		this.data = data;
	}

}
