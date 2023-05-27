package com.sterndu.data.transfer;

import java.net.SocketException;
import java.util.*;
import java.util.function.BiConsumer;

import com.sterndu.data.transfer.basic.Socket;


// TODO: Auto-generated Javadoc
/**
 * The Class Connector.
 */
public class Connector {

	/**
	 * The DataPlusType.
	 */
	public static final class DataPlusType {
		private final int type;
		private final byte[] data;

		public DataPlusType(int type, byte[] data) {
			this.type = type;
			this.data = data;
		}

		public int type() {
			return type;
		}

		public byte[] data() {
			return data;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || obj.getClass() != this.getClass()) return false;
			DataPlusType that = (DataPlusType) obj;
			return this.type == that.type &&
					Arrays.equals(this.data, that.data);
		}

		@Override
		public int hashCode() {
			return Objects.hash(type, Arrays.hashCode(data));
		}

		@Override
		public String toString() {
			return "DataPlusType[" +
					"type=" + type + ", " +
					"data=" + Arrays.toString(data) + ']';
		}


	}

	/** The sock. */
	private final Socket sock;

	/** The type. */
	private final byte type;

	/** The handle. */
	private BiConsumer<Byte, byte[]> handle;

	/** The packets. */
	private final List<DataPlusType> packets;

	/** The handle disabled. */
	private boolean handleDisabled;

	/**
	 * Instantiates a new connector.
	 *
	 * @param sock the sock
	 * @param type the type
	 */
	public Connector(Socket sock, byte type) {
		this.sock	= sock;
		this.type	= type;
		packets		= new ArrayList<>();
		enableHandle();
	}

	/**
	 * Available packeets.
	 *
	 * @return the int
	 */
	public int availablePackets() {
		return packets.size();
	}

	/**
	 * Disable handle.
	 */
	public void disableHandle() {
		handleDisabled=true;
		sock.setHandle(type, null);
	}

	/**
	 * Enable handle.
	 */
	public void enableHandle() {
		handleDisabled=false;
		sock.setHandle(type, (typ, data) -> {
			byte	typee	= data[0];
			byte[]	dat		= new byte[data.length - 1];
			System.arraycopy(data, 1, dat, 0, dat.length);
			if (handle == null) packets.add(new DataPlusType(typee, dat));
			else handle.accept(typee, dat);
		});
	}

	/**
	 * Gets the handle.
	 *
	 * @return the handle
	 */
	public BiConsumer<Byte, byte[]> getHandle() { return handle; }

	/**
	 * Gets the packet.
	 *
	 * @return the packet
	 */
	public DataPlusType getPacket() {
		if (availablePackets() > 0) return packets.remove(0);
		return null;
	}

	/**
	 * Gets the sock.
	 *
	 * @return the sock
	 */
	public Socket getSock() { return sock; }

	/**
	 * Gets the type.
	 *
	 * @return the type
	 */
	public int getType() { return type; }

	/**
	 * Checks if is handle disabled.
	 *
	 * @return true, if is handle disabled
	 */
	public boolean isHandleDisabled() { return handleDisabled; }

	/**
	 * Request resend.
	 *
	 * @throws SocketException the socket exception
	 */
	public void requestResend() throws SocketException { sock.requestResend(); }

	/**
	 * Send data.
	 *
	 * @param type the type
	 * @param data the data
	 *
	 * @throws SocketException the socket exception
	 */
	public final void sendData(byte type, byte[] data) throws SocketException {
		byte[] send = new byte[data.length + 1];
		send[0] = type;
		System.arraycopy(data, 0, send, 1, data.length);
		sock.sendData(this.type, send);

	}

	/**
	 * Send data with type 1.
	 *
	 * @param data the data
	 *
	 * @throws SocketException the socket exception
	 */
	public final void sendData(byte[] data) throws SocketException { sendData((byte) 1, data); }

	/**
	 * Sets the handle.
	 *
	 * @param handle the handle
	 */
	public void setHandle(BiConsumer<Byte, byte[]> handle) {
		this.handle = handle;
		for (DataPlusType dpt : packets) handle.accept((byte) dpt.type(), dpt.data());
	}

}
