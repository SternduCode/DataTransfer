package com.sterndu.data.transfer;

import com.sterndu.data.transfer.basic.ServerSocket;
import com.sterndu.data.transfer.basic.Socket;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class ConnectionUtil {

	public static ServerSocket host(int port, @Deprecated boolean closeThreadAfterConnectionLost,
			boolean parallelConnections, int connections,
			BiConsumer<Socket, ServerSocket> method) throws IOException {
		final ServerSocket hc = new ServerSocket(port);
		if (method != null) {
			final AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			if (parallelConnections) for (int i = 0; i < connections; i++) {
				final Socket c = hc.accept();
				final Thread t = new Thread(() -> method.accept(c, hc), i + "-Host");
				t.setDaemon(false);
				t.start();
			}
			else do {
				final Socket c = hc.accept();
				final Thread t2 = new Thread(() -> method.accept(c, hc), "0-Host");
				t2.setDaemon(false);
				t2.start();

			} while (!cTACL.get());
		}
		return hc;
	}

}
