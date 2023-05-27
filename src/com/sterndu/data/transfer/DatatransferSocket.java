package com.sterndu.data.transfer;

import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;

import com.sterndu.util.kotlin.Entry;

// TODO: Auto-generated Javadoc
/**
 * The Class DatatransferSocket.
 */
public abstract class DatatransferSocket extends Socket {

	/** The initialized. */
	protected boolean initialized = true;

	/** The shutdown hook. */
	protected Consumer<DatatransferSocket> shutdownHook = s -> {};

	/** The md. */
	protected MessageDigest md;

	/** The send lock. */
	protected Object recLock = new Object(), sendLock = new Object();

	/** The recv vector. */
	protected Vector<Packet> recvVector = new Vector<>();

	/** The delayed send. */
	protected List<Packet> delayed_send = new ArrayList<>();

	/** The handles. */
	protected Map<Byte, Entry<Class<?>, BiConsumer<Byte, byte[]>>> handles = new HashMap<>();

	/**
	 * Instantiates a new datatransfer socket.
	 */
	public DatatransferSocket() {}

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public DatatransferSocket(InetAddress address, int port) throws IOException {
		super(address, port);
	}

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param address the address
	 * @param port the port
	 * @param localAddr the local addr
	 * @param localPort the local port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public DatatransferSocket(InetAddress address, int port, InetAddress localAddr, int localPort) throws IOException {
		super(address, port, localAddr, localPort);
	}

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param proxy the proxy
	 */
	public DatatransferSocket(Proxy proxy) {
		super(proxy);
	}

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @throws UnknownHostException the unknown host exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public DatatransferSocket(String host, int port) throws UnknownHostException, IOException {
		super(host, port);
	}

	/**
	 * Instantiates a new datatransfer socket.
	 *
	 * @param host the host
	 * @param port the port
	 * @param localAddr the local addr
	 * @param localPort the local port
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public DatatransferSocket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
		super(host, port, localAddr, localPort);
	}

	/**
	 * Checks if is initialized.
	 *
	 * @return true, if is initialized
	 */
	public boolean isInitialized() { return initialized; }


}
