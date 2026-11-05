package com.ialfaro.tokio.game;

import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The lines the player reads in the chat: short, plain sentences, red only for errors. */
final class Chat {
	static final String CONNECTED = "Connected to Tokio.";
	static final String DISCONNECTED = "Disconnected from Tokio.";
	static final String NOT_CONNECTED = "Not connected to Tokio. Turn on Minecraft in Tokio's Games page, then type /tokio connect.";
	static final String LASER = "A laser is aiming at you.";
	static final String SUPER_LASER = "A super laser is aiming at you.";
	static final String SUPER_DUPER_LASER = "A super duper laser is aiming at you.";
	static final String TURNED_BACK = "Turned back.";
	private static final String TURN_ON = "Turn on Minecraft in Tokio's Games page.";

	private Chat() {
	}

	static String turnedInto(Component mob, OptionalInt seconds) {
		if (seconds.isEmpty()) return "Turned into " + mob.getString() + ".";
		return "Turned into " + mob.getString() + " for " + duration(seconds.getAsInt()) + ".";
	}

	private static String duration(int seconds) {
		return seconds + (seconds == 1 ? " second" : " seconds");
	}

	// A form waiting its turn behind a timed one, and the others that came before it
	static String waitsFor(Component mob, OptionalInt seconds, int ahead) {
		String when = ahead == 0 ? " next" : " after " + ahead + (ahead == 1 ? " other form" : " other forms");
		return "Turns into " + mob.getString() + when + (seconds.isEmpty() ? "." : ", for " + duration(seconds.getAsInt()) + ".");
	}

	static String notAMob(Component type) {
		return type.getString() + " isn't a mob.";
	}

	static String connecting(Address address) {
		return "Connecting to Tokio at " + address + "…";
	}

	static String meteors(int count) {
		return count == 1 ? "A meteor is falling." : count + " meteors are falling.";
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
