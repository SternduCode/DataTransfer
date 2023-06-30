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
import com.sterndu.util.*;
import com.sterndu.util.interfaces.ThrowingRunnable;

// TODO: Auto-generated Javadoc
/**
 * The Class Socket.
 */
public class Socket extends DatatransferSocket {

	/** The host. */
	protected boolean host = false;

	/**
	 * Instantiates a new socket.
	 */
	protected Socket() {}

	/**
	 * Instantiates a new socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Socket(InetAddress address, int port) throws IOException {
		super(address, port);
		init(false);
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @param localAddr the local addr
	 * @param localPort the local port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Socket(InetAddress address, int port, InetAddress localAddr, int localPort)
			throws IOException {
		super(address, port, localAddr, localPort);
		init(false);
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @throws UnknownHostException the unknown host exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Socket(String host, int port) throws UnknownHostException, IOException {
		super(host, port);
		init(false);
	}

	/**
	 * Instantiates a new socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @param localAddr the local addr
	 * @param localPort the local port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Socket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
		super(host, port, localAddr, localPort);
		init(false);
	}

	/**
	 * Impl receive data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	protected byte[] implReceiveData(byte type, byte[] data) {
		return data;
	}

	/**
	 * Impl send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	protected byte[] implSendData(byte type, byte[] data) {
		return data;
	}

	/**
	 * Inits the.
	 *
	 * @param host the host
	 */
	protected void init(boolean host) {
		try {
			md = MessageDigest.getInstance("SHA-256");// SHA3-256
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
				final Packet data = receiveData();
				if (handles.containsKey(data.type())) Objects.requireNonNull(getHandle(data.type())).accept(data.type(), data.data());
				else recvVector.add(data);
			} catch (final CancellationException ignored) {

			}
			if (delayed_send.size() > 0 && initialized) {
				final Packet data = delayed_send.remove(0);
				sendData(data.type(), data.data());
			}
		}, "CheckForMsgs" + hashCode());
	}

	/**
	 * Receive data.
	 *
	 * @return the packet
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected final Packet receiveData() throws IOException {
		Packet packet;
		sync: synchronized (recLock) {
			byte[] b = new byte[5];
			if (Util.readXBytes(b, getInputStream(), b.length)) {
				final byte type = b[0];
				final int length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1);
				b = new byte[32];
				byte[] data = new byte[length];
				if (Util.readXBytes(data, getInputStream(), length)
						&& Util.readXBytes(b, getInputStream(), b.length) && Arrays.equals(b, md.digest(data))) {
					packet = new Packet(type, data);
					if ("true".equals(System.getProperty("debug"))) {
						if (data.length > 5000) data = Arrays.copyOfRange(data, 0, 5000);
						System.err.println(type + "r[length:" + length + ",data:" + Arrays.toString(data) + ",hash:"
								+ Arrays.toString(b));

					}
					break sync;
				}
				if ("true".equals(System.getProperty("debug"))) {
					if (data.length > 5000) data = Arrays.copyOfRange(data, 0, 5000);
					System.err.println(type + "f[length:" + length + ",data:" + Arrays.toString(data) + ",hash:"
							+ Arrays.toString(b));
				}
			}
			if ("true".equals(System.getProperty("debug"))) System.err.println("f" + Arrays.toString(b));
			return new Packet((byte) -128, new byte[0]);
		}
		return new Packet(packet.type(), implReceiveData(packet.type(), packet.data()));

		// type byte; length int; data byte[]; hash byte[32];
	}

	/**
	 * Send internal data.
	 *
	 * @param type the type
	 * @param data the data
	 * @throws SocketException the socket exception
	 */
	protected final void sendInternalData(byte type, byte[] data) throws SocketException {
		if (isClosed()) throw new SocketException("Soket closed!");
		synchronized (sendLock) {
			final byte[] modified_data = implSendData(type, data);
			final byte[] hash = md.digest(modified_data);
			final byte[]	length_bytes	= ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(modified_data.length).array();
			try {
				final OutputStream os = getOutputStream();
				os.write(type);
				os.write(length_bytes);
				os.write(modified_data);
				os.write(hash);
				if ("true".equals(System.getProperty("debug", "false")))
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

	}

	/**
	 * Sets the host.
	 *
	 * @param host the new host
	 */
	protected void setHost(boolean host) { this.host = host; }

	/**
	 * Close.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SocketException the socket exception
	 */
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

	/**
	 * Gets the handle.
	 *
	 * @param type the type
	 * @return the handle
	 */
	public final BiConsumer<Byte, byte[]> getHandle(byte type) {
		return hasHandle(type) ? handles.get(type).value() : null;
	}

	/**
	 * Gets the message count.
	 *
	 * @return the message count
	 */
	public int getMessageCount() { return recvVector.size(); }

	/**
	 * Gets the message from buffer.
	 *
	 * @return the message from buffer
	 */
	public Packet getMessageFromBuffer() {
		if (recvVector.isEmpty()) throw new EmptyStackException();
		return recvVector.remove(0);
	}

	/**
	 * Checks for handle.
	 *
	 * @param type the type
	 * @return true, if successful
	 */
	public final boolean hasHandle(byte type) {
		return handles.containsKey(type);
	}

	/**
	 * Checks for message.
	 *
	 * @return true, if successful
	 */
	public boolean hasMessage() {
		return !recvVector.isEmpty();
	}

	/**
	 * Checks if is host.
	 *
	 * @return true, if is host
	 */
	public boolean isHost() { return host; }

	/**
	 * Request resend.
	 *
	 * @throws SocketException the socket exception
	 */
	public void requestResend() throws SocketException {
		sendInternalData((byte) 0, new byte[0]);
	}

	/**
	 * Send close.
	 *
	 * @throws SocketException the socket exception
	 */
	public void sendClose() throws SocketException {
		if (!isClosed() && isConnected())
			sendInternalData((byte) -1, new byte[0]);
	}

	/**
	 * Send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @throws SocketException the socket exception
	 */
	public final void sendData(byte type, byte[] data) throws SocketException {
		if (!initialized) {
			delayed_send.add(new Packet(type, data));
			return;
		}
		if (isClosed()) throw new SocketException("Socket closed!");
		synchronized (sendLock) {
			if (isClosed()) throw new SocketException("Socket closed!");
			final byte[]	modified_data	= implSendData(type, data);
			final byte[]	hash			= md.digest(modified_data);
			final byte[]	length_bytes	= ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(modified_data.length).array();
			try {
				final OutputStream os = getOutputStream();
				os.write(type);
				os.write(length_bytes);
				os.write(modified_data);
				os.write(hash);
				if ("true".equals(System.getProperty("debug", "false")))
					System.err.println(
							type + "s[length_bytes:" + Arrays.toString(length_bytes) + ", length:"
									+ modified_data.length
									+ ",data:" + Arrays.toString(modified_data) + ",hash:" + Arrays.toString(hash));
			} catch (final SocketException e) {
				try {
					close();
				} catch (IOException ex) {
					ex.initCause(e);
					ex.printStackTrace();
				}
			} catch (final IOException e) {
				e.printStackTrace();
				delayed_send.add(new Packet(type, data));
			}
		}

	}

	/**
	 * Sets the handle.
	 *
	 * @param type the type
	 * @param handle the handle
	 * @return true, if successful
	 */
	public final boolean setHandle(byte type, BiConsumer<Byte, byte[]> handle) {
		try {
			final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
			if (!handles.containsKey(type) || handles.get(type).key().equals(caller)) {
				if (handle != null) {
					if (handles.containsKey(type)) {
						Iterator<Packet> it = recvVector.iterator();
						while (it.hasNext()) {
							Packet p = it.next();
							if (p.type() == type) {
								handle.accept(p.type(), p.data());
								it.remove();
							}
						}
					}
					handles.put(type, new Entry<>(caller, handle));
				} else handles.remove(type);
				return true;
			}
			return false;
		} catch (final Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Sets the shutdown hook.
	 *
	 * @param hook the new shutdown hook
	 */
	public void setShutdownHook(Consumer<DatatransferSocket> hook) { shutdownHook = hook; }

}
