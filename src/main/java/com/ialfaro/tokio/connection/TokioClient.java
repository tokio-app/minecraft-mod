package com.ialfaro.tokio.connection;

/**
 * The WebSocket to Tokio (see PROTOCOL.md): it runs each command Tokio sends
 * through {@link Commands} and answers it. One connection at a time.
 */
public final class TokioClient {
	private final Commands commands;
	private final ConnectionListener listener;

	public TokioClient(Commands commands, ConnectionListener listener) {
		this.commands = commands;
		this.listener = listener;
	}

	/**
	 * Connects to Tokio at host:port, closing any connection first. Returns at
	 * once; the listener hears how it went.
	 */
	public void connect(String host, int port) {
		throw new UnsupportedOperationException("Not built yet");
	}

	/** Closes the connection, if any, with code 1000. */
	public void disconnect() {
		throw new UnsupportedOperationException("Not built yet");
	}

	public boolean isConnected() {
		return false;
	}
}
