package com.sterndu.data.transfer;

import java.io.IOException;
import java.net.*;

public abstract class DatatransferServerSocket extends ServerSocket {
	public DatatransferServerSocket() throws IOException {}

	public DatatransferServerSocket(int port) throws IOException {
		super(port);
	}

	public DatatransferServerSocket(int port, int backlog) throws IOException {
		super(port, backlog);
	}

	public DatatransferServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
		super(port, backlog, bindAddr);
	}

	public DatatransferServerSocket(SocketImpl impl) {
		super(impl);
	}

	@Override
	public abstract Socket accept();
}
