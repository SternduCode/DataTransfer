package com.sterndu.data.transfer.secure;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.interfaces.DHPublicKey;
import com.sterndu.encryption.*;

public class Socket extends com.sterndu.data.transfer.basic.Socket {

	protected byte mode = 0;

	protected CrypterList cl;

	protected DiffieHellman dh;

	protected Crypter crypter;

	public Socket() {}

	public Socket(InetAddress address, int port) throws IOException {
		super(address, port);
	}

	public Socket(InetAddress address, int port, InetAddress localAddr, int localPort) throws IOException {
		super(address, port, localAddr, localPort);
	}

	public Socket(String host, int port) throws UnknownHostException, IOException {
		super(host, port);
	}

	public Socket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
		super(host, port, localAddr, localPort);
	}

	@Override
	protected byte[] implRecieveData(byte type, byte[] data) {
		return switch (type) {
			case -1, -2, -3, -4, -5 -> data;
			default -> crypter.decrypt(data);
		};
	}

	@Override
	protected byte[] implSendData(byte type, byte[] data) {
		return switch (type) {
			case -1, -2, -3, -4, -5 -> data;
			default -> crypter.encrypt(data);
		};
	}

	@Override
	protected void init(boolean host) {
		try {
			this.host = host;
			initialized = false;
			md = MessageDigest.getInstance("SHA3-256");
			dh = new DiffieHellman();
			if (host) startHandshake();
			setHandle((byte) -2, (type, data) -> {
				try {
					initialized = false;
					dh.startHandshake();
					KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
					DHPublicKey key = (DHPublicKey) kf
							.generatePublic(new X509EncodedKeySpec(data, "DiffieHellman"));
					dh.initialize(key.getParams());
					dh.doPhase(key, true);
					sendData((byte) -3, dh.getPublicKey().getEncoded());
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
					dh.doPhase(key, true);
					ByteBuffer bb = ByteBuffer.allocate(4 * CrypterList.getSupportedVersions().length);
					bb.asIntBuffer().put(CrypterList.getSupportedVersions());
					sendData((byte) -4, bb.array());
				} catch (NoSuchAlgorithmException | InvalidKeySpecException | SocketException e) {
					e.printStackTrace();
				}
			});
			setHandle((byte) -4, (type, data) -> {
				try {
					IntBuffer ib = ByteBuffer.wrap(data).asIntBuffer();
					List<Integer> li = new ArrayList<>();
					while (ib.hasRemaining())
						li.add(ib.get());
					List<Integer> li2 = new ArrayList<>();
					for (int i: CrypterList.getSupportedVersions()) if (li.contains(i)) li2.add(i);
					li2.sort((i, ii) -> CrypterList.getByVersion(i).compareTo(CrypterList.getByVersion(ii)));
					sendData((byte) -5, ByteBuffer.allocate(5).putInt(li2.get(li2.size() - 1)).put(mode).array());
					cl = CrypterList.getByVersion(li2.get(li2.size() - 1));
					crypter = cl.getByMode(mode);
					crypter.makeKey(dh.getSecret());
					initialized = true;
				} catch (SocketException e) {
					e.printStackTrace();
				}
			});
			setHandle((byte) -5, (type, data) -> {
				ByteBuffer bb = ByteBuffer.wrap(data);
				cl = CrypterList.getByVersion(bb.getInt());
				mode = bb.get();
				crypter = cl.getByMode(mode);
				crypter.makeKey(dh.getSecret());
				initialized = true;
			});
			super.init(host);
		} catch (NoSuchAlgorithmException | SocketException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	protected void setHost(boolean host) {
		super.setHost(host);
	}

	public DiffieHellman getDH() { return dh; }

	public void startHandshake() throws SocketException {
		initialized = false;
		dh.startHandshake();
		sendData((byte) -2, dh.getPublicKey().getEncoded());
	}

}
