package com.sterndu.data.transfer.basic;

import java.io.IOException;
import java.net.*;
import com.sterndu.data.transfer.DatatransferServerSocket;

public class ServerSocket extends DatatransferServerSocket {

	public ServerSocket() throws IOException {}

	public ServerSocket(int port) throws IOException {
		super(port);
	}

	public ServerSocket(int port, int backlog) throws IOException {
		super(port, backlog);
	}

	public ServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
		super(port, backlog, bindAddr);
	}

	public ServerSocket(SocketImpl impl) {
		super(impl);
	}

	@Override
	public Socket accept() {
		try {
			final Socket s = new Socket();
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
