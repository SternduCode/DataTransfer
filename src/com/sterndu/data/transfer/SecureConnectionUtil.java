package com.sterndu.data.transfer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import com.sterndu.data.transfer.secure.*;

public class SecureConnectionUtil {

	public static Socket connect(String host, int port, @Deprecated boolean closeThreadAfterConnectionLost,
			Consumer<Socket> method)
					throws IOException {
		Socket cc = new Socket(host, port);
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

	public static ServerSocket host(int port, @Deprecated boolean closeThreadAfterConnectionLost,
			boolean parallelConnections, int connections,
			BiConsumer<Socket, ServerSocket> method) throws IOException {
		ServerSocket hc = new ServerSocket(port);
		if (method != null) {
			AtomicBoolean cTACL = new AtomicBoolean(closeThreadAfterConnectionLost);
			if (parallelConnections) for (int i = 0; i < connections; i++) {
				Socket c = hc.accept();
				if (c == null)
					continue;
				Thread t = new Thread((Runnable) () -> {
					method.accept(c, hc);
				}, i + "-Host");
				t.setDaemon(false);
				t.start();
			}
			else do {
				Socket c = hc.accept();
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
