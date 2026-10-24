package com.ialfaro.tokio.game;

import com.ialfaro.tokio.connection.ConnectionListener;
import com.ialfaro.tokio.connection.TokioClient;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The open world's connection to Tokio, and what it tells the host. Used on the server thread. */
public final class WorldConnection implements ConnectionListener {
	// One world at a time runs in the integrated server
	private static WorldConnection current;

	private final MinecraftServer server;
	private final TokioClient client;
	// Written on the server thread, read on the connection's
	private volatile Address address = Address.DEFAULT;

	private WorldConnection(MinecraftServer server) {
		this.server = server;
		this.client = new TokioClient(new CommandRunner(server), this);
	}

	/** The world's connection, made on first use. */
	static WorldConnection of(MinecraftServer server) {
		if (current == null || current.server != server) {
			close(current);
			current = new WorldConnection(server);
		}
		return current;
	}

	/** Disconnects the world, as it closes. */
	public static void close(MinecraftServer server) {
		if (current != null && current.server == server) {
			close(current);
			current = null;
		}
	}

	private static void close(WorldConnection connection) {
		if (connection != null) connection.client.disconnect();
	}

	void connect(Address address) {
		this.address = address;
		client.connect(address.host(), address.port());
	}

	void disconnect() {
		client.disconnect();
	}

	boolean isConnected() {
		return client.isConnected();
	}

	Address address() {
		return address;
	}

	@Override
	public void connected() {
		tell(host -> Chat.say(host, Chat.CONNECTED));
	}

	@Override
	public void closed(int code, String reason) {
		tell(host -> Chat.say(host, Chat.disconnected(reason)));
	}

	@Override
	public void failed(String message) {
		String text = Chat.couldntConnect(address, message);
		tell(host -> Chat.error(host, text));
	}

	/** Tells the host on the server thread; the listener is called on the connection's. */
	private void tell(Consumer<ServerPlayer> message) {
		server.execute(() -> {
			// A stopped server runs the task here, off its thread: the world is gone
			if (!server.isSameThread()) return;
			ServerPlayer host = CommandRunner.host(server);
			if (host != null) message.accept(host);
		});
	}
}
