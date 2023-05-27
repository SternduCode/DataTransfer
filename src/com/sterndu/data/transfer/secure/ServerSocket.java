package com.sterndu.data.transfer.secure;

import java.io.IOException;
import java.net.*;

public class ServerSocket extends com.sterndu.data.transfer.basic.ServerSocket {

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

	@Override
	public Socket accept() throws IOException {
		Socket s = new Socket();
		s.setHost(true);
		super.implAccept(s);
		s.init(true);
		return s;
	}

}
