package com.ialfaro.tokio.connection;

import static com.ialfaro.tokio.connection.FakeTokio.request;
import static com.ialfaro.tokio.connection.FakeTokio.take;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.Opcode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokioClientTest {
	private FakeTokio tokio;
	private TokioClient client;
	/** What the listener heard, as "connected", "closed <code> <reason>" or "failed <message>". */
	private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
	/** The command lines the game was asked to run. */
	private final BlockingQueue<String> ran = new LinkedBlockingQueue<>();
	private final ExecutorService gameThreads = Executors.newFixedThreadPool(8);

	@BeforeEach
	void start() throws InterruptedException {
		tokio = new FakeTokio();
		// "say ..." runs, anything else fails; each completes on another thread
		Commands commands = commandLine -> {
			ran.add(commandLine);
			return CompletableFuture.supplyAsync(() -> commandLine.startsWith("say ")
					? new CommandResult(true, "Said " + commandLine.substring(4))
					: new CommandResult(false, "Unknown command"), gameThreads);
		};
		client = new TokioClient(commands, new ConnectionListener() {
			@Override
			public void connected() {
				events.add("connected");
			}

			@Override
			public void closed(int code, String reason) {
				events.add("closed " + code + " " + reason);
			}

			@Override
			public void failed(String message) {
				events.add("failed " + message);
			}
		});
	}

	@AfterEach
	void stop() throws InterruptedException {
		client.disconnect();
		tokio.stop(1000);
		gameThreads.shutdownNow();
	}

	private WebSocket connect() throws InterruptedException {
		client.connect("localhost", tokio.getPort());
		assertEquals("connected", take(events));
		return tokio.nextOpen().socket();
	}

	private static void assertAnswer(JsonObject answer, String requestId, int statusCode, String statusMessage) {
		JsonObject header = answer.getAsJsonObject("header");
		assertEquals(requestId, header.get("requestId").getAsString());
		assertEquals("commandResponse", header.get("messagePurpose").getAsString());
		JsonObject body = answer.getAsJsonObject("body");
		assertEquals(statusCode, body.get("statusCode").getAsInt());
		assertEquals(statusMessage, body.get("statusMessage").getAsString());
	}

	/** Waits a moment to show nothing more comes. */
	private void assertNoEvent() throws InterruptedException {
		assertNull(events.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test
	void offersTheProtocolWithoutAnOrigin() throws InterruptedException {
		client.connect("localhost", tokio.getPort());
		assertEquals("connected", take(events));
		FakeTokio.Opened opened = tokio.nextOpen();
		assertTrue(client.isConnected());
		assertEquals("tokio.java.v1", opened.handshake().getFieldValue("Sec-WebSocket-Protocol"));
		assertEquals("tokio.java.v1", ((Draft_6455) opened.socket().getDraft()).getProtocol().getProvidedProtocol());
		assertFalse(opened.handshake().hasFieldValue("Origin"));
	}

	@Test
	void runsARequestAndAnswersIt() throws InterruptedException {
		WebSocket socket = connect();
		socket.send(request("a", "say hi"));
		assertAnswer(tokio.nextAnswer(), "a", 0, "Said hi");
		socket.send(request("b", "nope"));
		assertAnswer(tokio.nextAnswer(), "b", -1, "Unknown command");
	}

	@Test
	void answersFiftyRequestsAtOnce() throws InterruptedException {
		WebSocket socket = connect();
		Set<String> sent = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			sent.add("r" + i);
			socket.send(request("r" + i, "say " + i));
		}
		Set<String> answered = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			JsonObject answer = tokio.nextAnswer();
			String id = answer.getAsJsonObject("header").get("requestId").getAsString();
			assertAnswer(answer, id, 0, "Said " + id.substring(1));
			answered.add(id);
		}
		assertEquals(sent, answered);
	}

	@Test
	void gathersAFragmentedMessage() throws InterruptedException {
		WebSocket socket = connect();
		byte[] message = request("a", "say in pieces").getBytes(StandardCharsets.UTF_8);
		int third = message.length / 3;
		socket.sendFragmentedFrame(Opcode.TEXT, ByteBuffer.wrap(message, 0, third), false);
		socket.sendFragmentedFrame(Opcode.TEXT, ByteBuffer.wrap(message, third, third), false);
		socket.sendFragmentedFrame(Opcode.TEXT, ByteBuffer.wrap(message, 2 * third, message.length - 2 * third), true);
		assertAnswer(tokio.nextAnswer(), "a", 0, "Said in pieces");
	}

	@Test
	void ignoresWhatItDoesntUnderstand() throws InterruptedException {
		WebSocket socket = connect();
		for (String junk : List.of(
				"not json",
				"[]",
				"{}",
				"{\"header\":{\"requestId\":\"x\",\"messagePurpose\":\"event\"},\"body\":{\"commandLine\":\"say x\"}}",
				"{\"header\":{\"messagePurpose\":\"commandRequest\"},\"body\":{\"commandLine\":\"say x\"}}",
				"{\"header\":{\"requestId\":\"x\",\"messagePurpose\":\"commandRequest\"},\"body\":{}}")) {
			socket.send(junk);
		}
		socket.send(request("a", "say still here"));
		assertAnswer(tokio.nextAnswer(), "a", 0, "Said still here");
		assertEquals(List.of("say still here"), List.copyOf(ran));
		assertTrue(client.isConnected());
	}

	@Test
	void refusesBedrocksHandshake() throws InterruptedException {
		WebSocket socket = connect();
		socket.send(request("a", "enableencryption \"key\" \"salt\""));
		assertAnswer(tokio.nextAnswer(), "a", -1, "Update Tokio to use the Java mod.");
		assertTrue(ran.isEmpty());
	}

	@Test
	void hearsTokiosCloseCodeAndReason() throws InterruptedException {
		connect().close(4000, "Another game connected to Tokio.");
		assertEquals("closed 4000 Another game connected to Tokio.", take(events));
		assertFalse(client.isConnected());

		connect().close(1001, "Minecraft was turned off in Tokio.");
		assertEquals("closed 1001 Minecraft was turned off in Tokio.", take(events));
	}

	@Test
	void hearsADroppedConnection() throws InterruptedException {
		// Closes the TCP connection without a close frame: the JDK calls
		// onClose with 1006 (abnormal closure) and an empty reason itself.
		connect().closeConnection(1006, "");
		assertEquals("closed 1006 ", take(events));
		assertFalse(client.isConnected());
	}

	@Test
	void failsWhenNothingListens() throws Exception {
		int port;
		try (ServerSocket free = new ServerSocket(0)) {
			port = free.getLocalPort();
		}
		client.connect("localhost", port);
		assertEquals("failed Tokio isn't listening there.", take(events));
		assertFalse(client.isConnected());
	}

	@Test
	void disconnectsQuietly() throws InterruptedException {
		connect();
		client.disconnect();
		assertFalse(client.isConnected());
		assertEquals(1000, tokio.nextClose());
		assertNoEvent();
		client.disconnect();
	}

	@Test
	void aConnectReplacesTheConnection() throws InterruptedException {
		WebSocket old = connect();
		client.connect("localhost", tokio.getPort());
		assertEquals("connected", take(events));
		WebSocket next = tokio.nextOpen().socket();
		assertEquals(1000, tokio.nextClose());
		assertFalse(old.isOpen());
		assertNoEvent();

		next.send(request("a", "say new"));
		assertAnswer(tokio.nextAnswer(), "a", 0, "Said new");
	}
}
