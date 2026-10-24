package com.ialfaro.tokio.connection;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

/** Stands in for Tokio's MinecraftConnection: it accepts only tokio.java.v1. */
final class FakeTokio extends WebSocketServer {
	static final long WAIT = 5;

	/** Each connection as it opens, with its handshake. */
	final BlockingQueue<Opened> opened = new LinkedBlockingQueue<>();
	/** The answers the mod sends. */
	final BlockingQueue<JsonObject> answers = new LinkedBlockingQueue<>();
	/** The close codes each connection ended with. */
	final BlockingQueue<Integer> closes = new LinkedBlockingQueue<>();
	private final CountDownLatch started = new CountDownLatch(1);

	record Opened(WebSocket socket, ClientHandshake handshake) {
	}

	FakeTokio() throws InterruptedException {
		super(new InetSocketAddress("localhost", 0), List.of(new Draft_6455(List.of(), List.of(new Protocol("tokio.java.v1")))));
		setReuseAddr(true);
		start();
		started.await(WAIT, TimeUnit.SECONDS);
	}

	/** Waits for the next connection to open. */
	Opened nextOpen() throws InterruptedException {
		return take(opened);
	}

	JsonObject nextAnswer() throws InterruptedException {
		return take(answers);
	}

	int nextClose() throws InterruptedException {
		return take(closes);
	}

	static String request(String requestId, String commandLine) {
		JsonObject header = new JsonObject();
		header.addProperty("version", 1);
		header.addProperty("requestId", requestId);
		header.addProperty("messageType", "commandRequest");
		header.addProperty("messagePurpose", "commandRequest");
		JsonObject origin = new JsonObject();
		origin.addProperty("type", "player");
		JsonObject body = new JsonObject();
		body.addProperty("version", 42);
		body.addProperty("commandLine", commandLine);
		body.add("origin", origin);
		JsonObject message = new JsonObject();
		message.add("header", header);
		message.add("body", body);
		return message.toString();
	}

	static <T> T take(BlockingQueue<T> queue) throws InterruptedException {
		T next = queue.poll(WAIT, TimeUnit.SECONDS);
		assertNotNull(next, "timed out");
		return next;
	}

	@Override
	public void onStart() {
		started.countDown();
	}

	@Override
	public void onOpen(WebSocket socket, ClientHandshake handshake) {
		opened.add(new Opened(socket, handshake));
	}

	@Override
	public void onMessage(WebSocket socket, String message) {
		answers.add(JsonParser.parseString(message).getAsJsonObject());
	}

	@Override
	public void onClose(WebSocket socket, int code, String reason, boolean remote) {
		closes.add(code);
	}

	@Override
	public void onError(WebSocket socket, Exception error) {
	}
}
