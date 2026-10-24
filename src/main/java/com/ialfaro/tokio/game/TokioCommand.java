package com.ialfaro.tokio.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /tokio}, {@code /tokio connect [address]} and {@code /tokio disconnect}.
 * Only the singleplayer world's host can use them, cheats or not: a LAN guest
 * could otherwise point the host's world at their own socket.
 */
public final class TokioCommand {
	private TokioCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tokio")
				.requires(TokioCommand::isHost)
				.executes(TokioCommand::status)
				.then(Commands.literal("connect")
						.executes(context -> connect(context, ""))
						.then(Commands.argument("address", StringArgumentType.greedyString())
								.executes(context -> connect(context, StringArgumentType.getString(context, "address")))))
				.then(Commands.literal("disconnect").executes(TokioCommand::disconnect)));
	}

	private static boolean isHost(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null && player == CommandRunner.host(source.getServer());
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		WorldConnection connection = WorldConnection.of(context.getSource().getServer());
		ServerPlayer host = context.getSource().getPlayer();
		Chat.say(host, connection.isConnected() ? Chat.connectedTo(connection.address()) : Chat.NOT_CONNECTED);
		return 1;
	}

	private static int connect(CommandContext<CommandSourceStack> context, String text) {
		ServerPlayer host = context.getSource().getPlayer();
		Address address;
		try {
			address = Address.parse(text);
		} catch (IllegalArgumentException e) {
			Chat.error(host, e.getMessage());
			return 0;
		}
		Chat.say(host, Chat.connecting(address));
		WorldConnection.of(context.getSource().getServer()).connect(address);
		return 1;
	}

	private static int disconnect(CommandContext<CommandSourceStack> context) {
		WorldConnection connection = WorldConnection.of(context.getSource().getServer());
		ServerPlayer host = context.getSource().getPlayer();
		// The client tells the listener nothing about a disconnect it was asked for
		connection.disconnect();
		Chat.say(host, Chat.DISCONNECTED);
		return 1;
	}
}
