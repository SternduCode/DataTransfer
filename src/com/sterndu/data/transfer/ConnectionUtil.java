package com.sterndu.data.transfer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.sterndu.util.Updater;
import com.sterndu.util.Util;
import com.sterndu.util.interfaces.ThrowingRunnable;

public class ConnectionUtil {

	public static class ClientConnection extends Socket {

		protected boolean host = false;

		protected boolean initialized = true;
		protected Runnable shutdownHook=()->{};

		protected Socket socket;

		protected MessageDigest md;
		protected Object recLock = new Object(), sendLock = new Object();
		protected Vector<Paket> recvVector = new Vector<>();
		protected Paket last_msg;
		protected List<Paket> delayed_send = new ArrayList<>();
		protected Map<Byte, Map.Entry<Class<?>, BiConsumer<Byte, byte[]>>> handles = new HashMap<>();
		protected ClientConnection() {}

		public ClientConnection(InetAddress address, int port) throws IOException {
			super(address, port);
			init(host);
		}

		public ClientConnection(InetAddress address, int port, InetAddress localAddr, int localPort)
				throws IOException {
			super(address, port, localAddr, localPort);
			init(host);
		}

		public ClientConnection(String host, int port) throws UnknownHostException, IOException {
			super(host, port);
			init(this.host);
		}

		public ClientConnection(String host, int port, InetAddress localAddr, int localPort) throws IOException {
			super(host, port, localAddr, localPort);
			init(this.host);
		}

		@Override
		public synchronized void close() throws IOException,SocketException {
			synchronized (sendLock) {
				synchronized (recLock) {
					sendClose();
					shutdownOutput();
					shutdownInput();
					Updater.getInstance().remove("CheckForMsgs" + hashCode());
					shutdownHook.run();
					super.close();
				}
			}
		}

		public final BiConsumer<Byte,byte[]> getHandle(byte type) {
			return hasHandle(type)?handles.get(type).getValue():null;
		}

		public int getMessageCount() { return recvVector.size(); }

		public Paket getMessageFromBuffer() {
			if (recvVector.isEmpty()) {
				throw new EmptyStackException();
			}
			return recvVector.remove(0);
		}

		public final boolean hasHandle(byte type) {
			return handles.containsKey(type);
		}

		public boolean hasMessage() {
			return !recvVector.isEmpty();
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
					} catch (final IOException e) {
						e.printStackTrace();
					}
				});
				setHandle((byte) 0, (type, data) -> {
					if (last_msg != null) {
						try {
							sendData(last_msg.type(), last_msg.data());
						} catch (final SocketException e) {
							e.printStackTrace();
						}
					}
				});
				setHandle((byte) -128, (type, data) -> {
					try {
						requestResend();
					} catch (final SocketException e) {
						e.printStackTrace();
					}
				});
			} catch (final NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			Updater.getInstance().add((ThrowingRunnable) () -> {
				if (!isClosed() && getInputStream().available() > 0) {
					try {
						final Paket data = recieveData().get();
						if (handles.containsKey(data.type())) {
							getHandle(data.type()).accept(data.type(), data.data());
						} else {
							recvVector.add(data);
						}
					} catch (final CancellationException e) {

					}
				}
				if (delayed_send.size() > 0 && initialized) {
					final Paket data = delayed_send.remove(0);
					sendData(data.type(), data.data());
				}
			}, "CheckForMsgs" + hashCode());
		}

		protected final FutureTask<Paket> recieveData() {
			final AtomicReference<FutureTask<Paket>> ar = new AtomicReference<>();
			final FutureTask<Paket> ft = new FutureTask<>(() -> {
				Paket paket;
				sync: synchronized (recLock) {
					byte[] b = new byte[5];
					if (Util.readXBytes(b, getInputStream(), b.length)) {
						final byte type = b[0];
						final int length = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt(1);
						b = new byte[32];
						final byte[] data = new byte[length];
						if (Util.readXBytes(data,getInputStream(),length) && Util.readXBytes(b,getInputStream(),b.length) && Arrays.equals(b,md.digest(data))) {
							paket = new Paket(type, data);
							System.out.println(type + "r[length:" + length + ",data:" + Arrays.toString(data) + ",hash:"
									+ Arrays.toString(b));
							break sync;
						}
						System.out.println(type + "f[length:" + length + ",data:" + Arrays.toString(data) + ",hash:"
								+ Arrays.toString(b));
					}
					System.out.println("f" + Arrays.toString(b));
					ar.get().cancel(false);
					return new Paket((byte) -128, new byte[0]);
				}
				return new Paket(paket.type(), implRecieveData(paket.type(), paket.data()));
			});
			ar.setRelease(ft);
			new Thread(ft).start();
			return ft;

			//type byte; length int; data byte[]; hash byte[];
		}

		public void removeLastSentMessage() {
			last_msg=null;
		}

		public void requestResend() throws SocketException {
			sendData((byte) 0, new byte[0]);
		}

		public void sendClose() throws SocketException {
			sendData((byte) -1,new byte[0]);
		}

		public final void sendData(byte type, byte[] data) throws SocketException {
			if (!initialized) {
				try {
					final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
					System.out.println(caller);
					caller.asSubclass(ClientConnection.class);
				} catch (ClassNotFoundException | ClassCastException e) {
					delayed_send.add(new Paket(type, data));
					return;
				}
			}
			if (!isClosed()) {
				synchronized (sendLock) {
					final byte[] modified_data = implSendData(type, data);
					final byte[] hash = md.digest(modified_data);
					final byte[] length_bytes = ByteBuffer.allocate(4).putInt(modified_data.length).array();
					try {
						final OutputStream os = getOutputStream();
						os.write(type);
						os.write(length_bytes);
						os.write(modified_data);
						os.write(hash);
						System.out.println(
								type + "s[length_bytes:" + Arrays.toString(length_bytes) + ", length:"
										+ modified_data.length
										+ ",data:" + Arrays.toString(modified_data) + ",hash:" + Arrays.toString(hash));
					} catch (final IOException e) {
						e.printStackTrace();
						delayed_send.add(new Paket(type, data));
						return;
					}
					last_msg = new Paket(type, data);
				}
			} else {
				throw new SocketException("Soket closed!");
			}

		}

		public final boolean setHandle(byte type,BiConsumer<Byte,byte[]> handle) {
			try {
				final Class<?> caller = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
				if (!handles.containsKey(type)) {
					handles.put(type, Map.entry(caller, handle));
					return true;
				} else if (handles.get(type).getKey().equals(caller)) {
					handles.put(type, Map.entry(caller, handle));
					return true;
				} else {
					return false;
				}
			} catch (final Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		public void setShutdownHook(Runnable hook) {
			shutdownHook=hook;
		}

	}

	public static class HostConnection extends ServerSocket {

		public HostConnection(int port) throws IOException {
			super(port);
		}

		@Override
		public Socket accept() {
			try {
				final ClientConnection s = new ClientConnection();
				super.implAccept(s);
				s.host = true;
				s.init(true);
				return s;
			} catch (final IOException e) {
				e.printStackTrace();
				return null;
			}
		}

	}

	public static record Paket(byte type, byte[] data) {

		public Paket(byte type, byte[] data) {
			this.type = type;
			this.data = data;
		}

	}

	public static ClientConnection connect(String host, int port, @Deprecated boolean closeThreadAfterConnectionLost,
			Consumer<ClientConnection> method)
					throws IOException {
		final ClientConnection cc=new ClientConnection(host, port);
		if (method != null) {
			final AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			new Thread((Runnable) () -> {
				do {
					method.accept(cc);
					try {
						Thread.sleep(5);
					} catch (final InterruptedException e) {
						e.printStackTrace();
					}
				} while (!cTACL.get());
			}, "0-Client") {
				@Override
				public void interrupt() {
					cTACL.set(true);
				}
			}.start();
		}
		return cc;
	}

	public static HostConnection host(int port, @Deprecated boolean closeThreadAfterConnectionLost,
			boolean parallelConnections, int connections,
			BiConsumer<ClientConnection, HostConnection> method) throws IOException {
		final HostConnection hc = new HostConnection(port);
		if (method != null) {
			final AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			if (parallelConnections) {
				for (int i = 0; i < connections; i++) {
					final ClientConnection c = (ClientConnection) hc.accept();
					if (c == null) {
						continue;
					}
					final Thread t = new Thread((Runnable) () -> {
						method.accept(c, hc);
					}, i + "-Host");
					t.setDaemon(false);
					t.start();
				}
			} else {
				do {
					final ClientConnection c = (ClientConnection) hc.accept();
					if (c == null) {
						continue;
					}
					final Thread t2 = new Thread(() -> {
						method.accept(c, hc);
					}, "0-Host");
					t2.setDaemon(false);
					t2.start();

				} while (!cTACL.get());
			}
		}
		return hc;
	}

}
