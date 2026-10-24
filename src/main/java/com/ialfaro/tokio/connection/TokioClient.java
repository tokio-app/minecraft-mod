package com.ialfaro.tokio.connection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * The WebSocket to Tokio (see PROTOCOL.md): it runs each command Tokio sends
 * through {@link Commands} and answers it. One connection at a time.
 *
 * <p>The listener is called with this client's lock held, so a connection
 * that was replaced or disconnected never reaches it afterwards. It must not
 * block, for example by waiting on the server thread.
 */
public final class TokioClient {
	private static final String PROTOCOL = "tokio.java.v1";
	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final Commands commands;
	private final ConnectionListener listener;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	// The only connection whose callbacks reach the listener; guarded by this
	private Connection current;

	public TokioClient(Commands commands, ConnectionListener listener) {
		this.commands = commands;
		this.listener = listener;
	}

	/**
	 * Connects to Tokio at host:port, closing any connection first. Returns at
	 * once; the listener hears how it went.
	 */
	public synchronized void connect(String host, int port) {
		disconnect();
		Connection connection = new Connection();
		current = connection;
		try {
			// This URI constructor puts an IPv6 literal like ::1 in brackets
			URI uri = new URI("ws", null, host, port, "/", null, null);
			// No Origin header: Tokio refuses upgrades that carry one
			http.newWebSocketBuilder()
					.subprotocols(PROTOCOL)
					.connectTimeout(TIMEOUT)
					.buildAsync(uri, connection)
					.whenComplete((socket, error) -> {
						if (error != null) connection.fail(error);
					});
		} catch (URISyntaxException | IllegalArgumentException e) {
			connection.fail(e);
		}
	}

	/**
	 * Closes the connection, if any, with code 1000, or drops a connect still
	 * pending. The listener hears nothing more from it. Doesn't block.
	 */
	public synchronized void disconnect() {
		if (current != null) {
			current.close();
			current = null;
		}
	}

	/** Whether a connection is open: from {@code connected} until it ends. */
	public synchronized boolean isConnected() {
		return current != null && current.socket != null;
	}

	private synchronized boolean isCurrent(Connection connection) {
		return current == connection;
	}

	private static String message(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof ConnectException) return "Tokio isn't listening there.";
		if (cause instanceof HttpTimeoutException) return "It took too long to answer.";
		return cause.getMessage() != null ? cause.getMessage() : cause.toString();
	}

	/** One connect: its socket, and the answers it sends. */
	private final class Connection implements WebSocket.Listener {
		// Set on open; guarded by the client's lock
		private WebSocket socket;
		private final StringBuilder text = new StringBuilder();
		// The JDK refuses a send while another is pending, so each waits for the last
		private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);

		/** Called with the client's lock held. */
		void close() {
			WebSocket open = socket;
			// Still connecting: onOpen aborts it
			if (open == null) return;
			open.sendClose(WebSocket.NORMAL_CLOSURE, "");
			// Don't keep the socket if Tokio never answers the close
			CompletableFuture.delayedExecutor(TIMEOUT.toSeconds(), TimeUnit.SECONDS).execute(open::abort);
		}

		void fail(Throwable error) {
			end(() -> listener.failed(message(error)));
		}

		/** Ends the current connection once: later callbacks are dropped. */
		private void end(Runnable callback) {
			synchronized (TokioClient.this) {
				if (current != this) return;
				current = null;
				callback.run();
			}
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			synchronized (TokioClient.this) {
				if (current != this) {
					webSocket.abort();
					return;
				}
				socket = webSocket;
				listener.connected();
			}
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			text.append(data);
			if (last) {
				String frame = text.toString();
				text.setLength(0);
				handle(webSocket, frame);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
			end(() -> listener.closed(code, reason));
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			// An I/O or protocol error ends the socket without onClose; a dropped
			// network comes through onClose as 1006 instead
			end(() -> {
				if (socket != null) listener.closed(1006, "");
				else listener.failed(message(error));
			});
		}

		private void handle(WebSocket webSocket, String frame) {
			String requestId;
			String commandLine;
			try {
				JsonObject message = JsonParser.parseString(frame).getAsJsonObject();
				JsonObject header = message.getAsJsonObject("header");
				if (!"commandRequest".equals(header.get("messagePurpose").getAsString())) return;
				requestId = header.get("requestId").getAsString();
				commandLine = message.getAsJsonObject("body").get("commandLine").getAsString();
			} catch (RuntimeException e) {
				// Bad JSON or missing fields: not a request, so it's ignored
				return;
			}
			// Frames that come after a disconnect don't run
			if (!isCurrent(this)) return;
			if (commandLine.startsWith("enableencryption")) {
				// Bedrock's handshake, from a Tokio that doesn't know the Java mod
				answer(webSocket, requestId, new CommandResult(false, "Update Tokio to use the Java mod."));
				return;
			}
			commands.run(commandLine)
					.exceptionally(e -> new CommandResult(false, "The command couldn't run."))
					.thenAccept(result -> answer(webSocket, requestId, result));
		}

		private void answer(WebSocket webSocket, String requestId, CommandResult result) {
			JsonObject header = new JsonObject();
			header.addProperty("requestId", requestId);
			header.addProperty("messagePurpose", "commandResponse");
			JsonObject body = new JsonObject();
			body.addProperty("statusCode", result.ok() ? 0 : -1);
			body.addProperty("statusMessage", result.message());
			JsonObject message = new JsonObject();
			message.add("header", header);
			message.add("body", body);
			String json = message.toString();
			synchronized (this) {
				sending = sending.handle((ignored, error) -> null).thenCompose(ignored -> webSocket.sendText(json, true));
			}
		}
	}
}
