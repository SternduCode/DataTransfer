package com.sterndu.data.transfer;

import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javax.crypto.interfaces.DHPublicKey;
import com.sterndu.encryption.DiffieHellmanAES;

public class SecureConnectionUtil {

	public static class ClientConnection extends ConnectionUtil.ClientConnection {

		protected DiffieHellmanAES dHAES;

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
		protected byte[] implRecieveData(byte type, byte[] data) {
			return switch (type) {
				case 0, -1, -2, -3, -128 -> data;
				default -> dHAES.decrypt(data);
			};
		}

		@Override
		protected byte[] implSendData(byte type, byte[] data) {
			return switch (type) {
				case 0, -1, -2, -3, -128 -> data;
				default -> dHAES.encrypt(data);
			};
		}

		@Override
		protected void init(boolean host) {
			try {
				initialized = false;
				md = MessageDigest.getInstance("SHA3-256");
				dHAES = new DiffieHellmanAES();
				if (host) startHandshake();
				setHandle((byte) -2, (type, data) -> {
					try {
						initialized = false;
						dHAES.startHandshake();
						KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
						DHPublicKey key = (DHPublicKey) kf
								.generatePublic(new X509EncodedKeySpec(data, "DiffieHellman"));
						dHAES.initialize(key.getParams());
						dHAES.doPhase(key, true);
						sendData((byte) -3, dHAES.getPublicKey().getEncoded());
						initialized = true;
					} catch (NoSuchAlgorithmException | InvalidKeySpecException
							| InvalidAlgorithmParameterException | SocketException e) {
						e.printStackTrace();
					}
				});
				setHandle((byte) -3, (type, data) -> {
					try {
						KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
						DHPublicKey key = (DHPublicKey) kf
								.generatePublic(new X509EncodedKeySpec(data, "DiffieHellman"));
						dHAES.doPhase(key, true);
						initialized = true;
					} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
						e.printStackTrace();
					}
				});
				super.init(host);
			} catch (NoSuchAlgorithmException | SocketException e1) {
				e1.printStackTrace();
			}
		}

		public DiffieHellmanAES getDHAES() { return dHAES; }

		public void startHandshake() throws SocketException {
			initialized = false;
			dHAES.startHandshake();
			sendData((byte) -2, dHAES.getPublicKey().getEncoded());
		}

	}

	public static class HostConnection extends ConnectionUtil.HostConnection {

		public HostConnection(int port) throws IOException {
			super(port);
		}

		@Override
		public Socket accept() {
			try {
				ClientConnection s = new ClientConnection();
				s.host = true;
				super.implAccept(s);
				s.init(true);
				return s;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}

	}

	public static ClientConnection connect(String host, int port, @Deprecated boolean closeThreadAfterConnectionLost,
			Consumer<ClientConnection> method)
					throws IOException {
		ClientConnection cc = new ClientConnection(host, port);
		if (method != null) {
			AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			new Thread((Runnable) () -> {
				do {
					method.accept(cc);
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
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
		HostConnection hc = new HostConnection(port);
		if (method != null) {
			AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			if (parallelConnections) for (int i = 0; i < connections; i++) {
				ClientConnection c = (ClientConnection) hc.accept();
				if (c == null)
					continue;
				Thread t = new Thread((Runnable) () -> {
					method.accept(c, hc);
				}, i + "-Host");
				t.setDaemon(false);
				t.start();
			}
			else do {
				ClientConnection c = (ClientConnection) hc.accept();
				if (c == null)
					continue;
				Thread t2 = new Thread(() -> {
					method.accept(c, hc);
				}, "0-Host");
				t2.setDaemon(false);
				t2.start();

			} while (!cTACL.get());
		}
		return hc;
	}

}
