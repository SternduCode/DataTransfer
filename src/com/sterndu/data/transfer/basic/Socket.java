package com.sterndu.data.transfer.basic;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import com.sterndu.data.transfer.*;
import com.sterndu.multicore.Updater;
import com.sterndu.util.Util;
import com.sterndu.util.interfaces.ThrowingRunnable;

public class Socket extends DatatransferSocket {

	protected boolean host = false;

	protected Socket() {}

	public Socket(InetAddress address, int port) throws IOException {
		super(address, port);
		init(host);
	}

	public Socket(InetAddress address, int port, InetAddress localAddr, int localPort)
			throws IOException {
		super(address, port, localAddr, localPort);
		init(host);
	}

	public Socket(String host, int port) throws UnknownHostException, IOException {
		super(host, port);
		init(this.host);
	}

	public Socket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
		super(host, port, localAddr, localPort);
		init(this.host);
	}

	protected byte[] implRecieveData(byte type, byte[] data) {
		return data;
	}

	protected byte[] implSendData(byte type, byte[] data) {
		return data;
	}

	protected void init(boolean host) {
		try {
			md = MessageDigest.getInstance("SHA3-256");
			setHandle((byte) -1, (type, data) -> {
				try {
					close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		} catch (final NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		Updater.getInstance().add((ThrowingRunnable) () -> {
			if (!isClosed() && getInputStream().available() > 0) try {
				final Packet data = recieveData();
				if (handles.containsKey(data.type())) getHandle(data.type()).accept(data.type(), data.data());
				else recvVector.add(data);
			} catch (final CancellationException e) {

			}
			if (delayed_send.size() > 0 && initialized) {
				final Packet data = delayed_send.remove(0);
				sendData(data.type(), data.data());
			}
		}, "CheckForMsgs" + hashCode());
	}

	protected final Packet recieveData() throws IOException {
		Packet packet;
		sync: synchronized (recLock) {
			byte[] b = new byte[5];
			if (Util.readXBytes(b, getInputStream(), b.length)) {
				final byte type = b[0];
				final int length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1);
				b = new byte[32];
				final byte[] data = new byte[length];
				if (Util.readXBytes(data, getInputStream(), length)
						&& Util.readXBytes(b, getInputStream(), b.length) && Arrays.equals(b, md.digest(data))) {
					packet = new Packet(type, data);
					if (System.getProperty("debug").equals("true"))
						System.err.println(type + "r[length:" + length + ",data:" + Arrays.toString(data) + ",hash:"
								+ Arrays.toString(b));
					break sync;
				}
				if (System.getProperty("debug").equals("true"))
					System.out.println(type + "f[length:" + length + ",data:" + Arrays.toString(data) + ",hash:"
							+ Arrays.toString(b));
			}
			if (System.getProperty("debug").equals("true"))
				System.out.println("f" + Arrays.toString(b));
			return new Packet((byte) -128, new byte[0]);
		}
		return new Packet(packet.type(), implRecieveData(packet.type(), packet.data()));
		// type byte; length int; data byte[]; hash byte[];
	}

	protected void setHost(boolean host) { this.host = host; }

	@Override
	public void close() throws IOException, SocketException {
		try {
			synchronized (recLock) {
				synchronized (sendLock) {
					if (!isClosed()) {
						shutdownHook.accept(this);
						shutdownOutput();
						shutdownInput();
						Updater.getInstance().remove("CheckForMsgs" + hashCode());
						super.close();
					}
				}
			}
		} catch (NullPointerException e) {
			Updater.getInstance().remove("CheckForMsgs" + hashCode());
			super.close();
		}
	}

	public final BiConsumer<Byte, byte[]> getHandle(byte type) {
		return hasHandle(type) ? handles.get(type).getValue() : null;
	}

	public int getMessageCount() { return recvVector.size(); }

	public Packet getMessageFromBuffer() {
		if (recvVector.isEmpty()) throw new EmptyStackException();
		return recvVector.remove(0);
	}

	public final boolean hasHandle(byte type) {
		return handles.containsKey(type);
	}

	public boolean hasMessage() {
		return !recvVector.isEmpty();
	}

	public boolean isHost() { return host; }

	public void requestResend() throws SocketException {
		sendData((byte) 0, new byte[0]);
	}

	public void sendClose() throws SocketException {
		sendData((byte) -1, new byte[0]);
	}

	public final void sendData(byte type, byte[] data) throws SocketException {
		if (!initialized) try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (System.getProperty("debug").equals("true"))
				System.out.println(caller);
			caller.asSubclass(Socket.class);
		} catch (ClassNotFoundException | ClassCastException e) {
			delayed_send.add(new Packet(type, data));
			return;
		}
		if (!isClosed()) synchronized (sendLock) {
			final byte[] modified_data = implSendData(type, data);
			final byte[] hash = md.digest(modified_data);
			final byte[] length_bytes = ByteBuffer.allocate(4).putInt(modified_data.length).array();
			try {
				final OutputStream os = getOutputStream();
				os.write(type);
				os.write(length_bytes);
				os.write(modified_data);
				os.write(hash);
				if (System.getProperty("debug").equals("true"))
					System.err.println(
							type + "s[length_bytes:" + Arrays.toString(length_bytes) + ", length:"
									+ modified_data.length
									+ ",data:" + Arrays.toString(modified_data) + ",hash:" + Arrays.toString(hash));
			} catch (final IOException e) {
				e.printStackTrace();
				delayed_send.add(new Packet(type, data));
				return;
			}
		}
		else throw new SocketException("Soket closed!");

	}

	public final boolean setHandle(byte type, BiConsumer<Byte, byte[]> handle) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (!handles.containsKey(type)) {
				handles.put(type, Map.entry(caller, handle));
				return true;
			} else if (handles.get(type).getKey().equals(caller)) {
				handles.put(type, Map.entry(caller, handle));
				return true;
			} else return false;
		} catch (final Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void setShutdownHook(Consumer<DatatransferSocket> hook) { shutdownHook = hook; }

}
