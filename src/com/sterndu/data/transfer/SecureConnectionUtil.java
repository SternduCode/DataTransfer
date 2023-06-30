package com.sterndu.data.transfer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import com.sterndu.data.transfer.secure.*;

public class SecureConnectionUtil {

	public static ServerSocket host(int port, @Deprecated boolean closeThreadAfterConnectionLost,
			boolean parallelConnections, int connections,
			BiConsumer<Socket, ServerSocket> method) throws IOException {
		ServerSocket hc = new ServerSocket(port);
		if (method != null) {
			AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			if (parallelConnections) for (int i = 0; i < connections; i++) {
				Socket c = hc.accept();
				if (c == null) continue;
				Thread t = new Thread(() -> method.accept(c, hc), i + "-Host");
				t.setDaemon(false);
				t.start();
			}
			else do {
				Socket c = hc.accept();
				if (c == null) continue;
				Thread t2 = new Thread(() -> method.accept(c, hc), "0-Host");
				t2.setDaemon(false);
				t2.start();

			} while (!cTACL.get());
		}
		return hc;
	}

}
