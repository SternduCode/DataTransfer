package com.sterndu.data.transfer;

import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;

public abstract class DatatransferSocket extends Socket {

	protected boolean initialized = true;
	protected Consumer<DatatransferSocket> shutdownHook = s -> {};
	protected MessageDigest md;
	protected Object recLock = new Object(), sendLock = new Object();
	protected Vector<Packet> recvVector = new Vector<>();
	protected List<Packet> delayed_send = new ArrayList<>();
	protected Map<Byte, Map.Entry<Class<?>, BiConsumer<Byte, byte[]>>> handles = new HashMap<>();

	public DatatransferSocket() {}

	public DatatransferSocket(InetAddress address, int port) throws IOException {
		super(address, port);
	}

	public DatatransferSocket(InetAddress address, int port, InetAddress localAddr, int localPort) throws IOException {
		super(address, port, localAddr, localPort);
	}

	public DatatransferSocket(Proxy proxy) {
		super(proxy);
	}

	public DatatransferSocket(SocketImpl impl) throws SocketException {
		super(impl);
	}

	public DatatransferSocket(String host, int port) throws UnknownHostException, IOException {
		super(host, port);
	}

	public DatatransferSocket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
		super(host, port, localAddr, localPort);
	}


}
