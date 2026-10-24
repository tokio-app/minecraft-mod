package com.ialfaro.tokio.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The lines the player reads in the chat: short, plain sentences, red only for errors. */
final class Chat {
	static final String CONNECTED = "Connected to Tokio.";
	static final String DISCONNECTED = "Disconnected from Tokio.";
	static final String NOT_CONNECTED = "Not connected to Tokio. Turn on Minecraft in Tokio's Games page, then type /tokio connect.";
	private static final String TURN_ON = "Turn on Minecraft in Tokio's Games page.";

	private Chat() {
	}

	static String connecting(Address address) {
		return "Connecting to Tokio at " + address + "…";
	}

	static String connectedTo(Address address) {
		return "Connected to Tokio at " + address + ".";
	}

	static String couldntConnect(Address address, String reason) {
		return join("Couldn't connect to Tokio at " + address + ".", reason, TURN_ON);
	}

	/** After Tokio or the network ended the connection, with Tokio's reason when there's one. */
	static String disconnected(String reason) {
		return join(DISCONNECTED, reason);
	}

	static void say(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal(text));
	}

	static void error(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	/** Joins sentences, skipping empty ones and ending each with a full stop. */
	static String join(String... sentences) {
		StringBuilder text = new StringBuilder();
		for (String sentence : sentences) {
			String s = sentence == null ? "" : sentence.strip();
			if (s.isEmpty()) continue;
			if (!text.isEmpty()) text.append(' ');
			text.append(s);
			if (!s.endsWith(".") && !s.endsWith("!") && !s.endsWith("?")) text.append('.');
		}
		return text.toString();
	}
}
