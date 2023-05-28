package com.sterndu.data.transfer.secure;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.interfaces.DHPublicKey;

import com.sterndu.encryption.*;
import com.sterndu.multicore.Updater;

// TODO: Auto-generated Javadoc
/**
 * The Class Socket.
 */
public class Socket extends com.sterndu.data.transfer.basic.Socket {

	/** The dh. */
	protected DiffieHellman dh;

	/** The crypter. */
	protected Crypter crypter;

	/**
	 * Instantiates a new socket.
	 */
	public Socket() {}

	/**
	 * Instantiates a new socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Socket(InetAddress address, int port) throws IOException {
		super(address, port);
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
	public Socket(InetAddress address, int port, InetAddress localAddr, int localPort) throws IOException {
		super(address, port, localAddr, localPort);
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
	}

	/**
	 * Impl recieve data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	@Override
	protected byte[] implReceiveData(byte type, byte[] data) {
		switch (type) {
			case 0:
			case -1:
			case -2:
			case -3:
			case -4:
			case -5:
				return data;
			default:
				return crypter.decrypt(data);
		}
	}

	/**
	 * Impl send data.
	 *
	 * @param type the type
	 * @param data the data
	 * @return the byte[]
	 */
	@Override
	protected byte[] implSendData(byte type, byte[] data) {
		switch (type) {
			case 0:
			case -1:
			case -2:
			case -3:
			case -4:
			case -5:
				return data;
			default:
				return crypter.encrypt(data);
		}
	}

	/**
	 * Inits the.
	 *
	 * @param host the host
	 */
	@Override
	protected void init(boolean host) {
		try {
			this.host = host;
			initialized = false;
			dh = new DiffieHellman();
			AtomicLong lastInitStageTime = new AtomicLong(System.currentTimeMillis());
			Updater.getInstance().add((Runnable) () -> {
				if (System.currentTimeMillis() - lastInitStageTime.get() > 2000) try {
					close();
					Updater.getInstance().remove("InitCheck" + hashCode());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}, "InitCheck" + hashCode());
			setHandle((byte) -2, (type, data) -> {
				try {
					ByteBuffer	bb			= ByteBuffer.wrap(data);
					int			keyLength	= bb.getInt();
					byte[]		keyData		= new byte[keyLength];
					bb.get(keyData);
					IntBuffer		ib	= bb.asIntBuffer();
					List<Integer>	li	= new ArrayList<>();
					while (ib.hasRemaining())
						li.add(ib.get());
					List<Integer> li2 = new ArrayList<>();
					for (int i : CrypterList.getSupportedVersions()) if (li.contains(i)) li2.add(i);
					Collections.sort(li2);
					initialized = false;
					dh.startHandshake();
					KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
					DHPublicKey key = (DHPublicKey) kf
							.generatePublic(new X509EncodedKeySpec(keyData, "DiffieHellman"));
					dh.initialize(key.getParams());
					dh.doPhase(key, true);
					lastInitStageTime.set(System.currentTimeMillis());
					byte[] pubKeyEnc = dh.getPublicKey().getEncoded();
					bb = ByteBuffer.allocate(4 + pubKeyEnc.length);
					bb.putInt(li2.get(li2.size() - 1));
					bb.put(pubKeyEnc);
					sendInternalData((byte) -3, bb.array());
					crypter = CrypterList.getByVersion(li2.get(li2.size() - 1));
					crypter.makeKey(dh.getSecret());
					initialized = true;
					Updater.getInstance().remove("InitCheck" + hashCode());
				} catch (NoSuchAlgorithmException | InvalidKeySpecException
						| InvalidAlgorithmParameterException | SocketException | InvalidKeyException e) {
					e.printStackTrace();
				}
			});// Test reduced number of calls && add hashing list avail stuff && add option to disable double hashing
			setHandle((byte) -3, (type, data) -> {
				try {
					lastInitStageTime.set(System.currentTimeMillis());
					ByteBuffer bb = ByteBuffer.wrap(data);
					crypter = CrypterList.getByVersion(bb.getInt());
					byte[] keyData = new byte[data.length - 4];
					bb.get(keyData);
					KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
					DHPublicKey key = (DHPublicKey) kf
							.generatePublic(new X509EncodedKeySpec(keyData, "DiffieHellman"));
					dh.doPhase(key, true);
					crypter.makeKey(dh.getSecret());
					initialized = true;
					Updater.getInstance().remove("InitCheck" + hashCode());
				} catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
					e.printStackTrace();
				}
			});
			super.init(host);
			if (host) startHandshake();
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Sets the host.
	 *
	 * @param host the new host
	 */
	@Override
	protected void setHost(boolean host) {
		super.setHost(host);
	}

	/**
	 * Gets the dh.
	 *
	 * @return the dh
	 */
	public DiffieHellman getDH() { return dh; }

	/**
	 * Start handshake.
	 *
	 * @throws SocketException the socket exception
	 */
	public void startHandshake() throws SocketException {
		initialized = false;
		dh.startHandshake();
		byte[]		pubKeyEnc	= dh.getPublicKey().getEncoded();
		ByteBuffer	bb			= ByteBuffer.allocate(4 + pubKeyEnc.length + 4 * CrypterList.getSupportedVersions().length);
		bb.putInt(pubKeyEnc.length);
		bb.put(pubKeyEnc);
		bb.asIntBuffer().put(CrypterList.getSupportedVersions());
		sendInternalData((byte) -2, bb.array());
	}

}
