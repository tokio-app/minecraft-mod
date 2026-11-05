package com.ialfaro.tokio.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.OptionalInt;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * {@code /tokio}, {@code /tokio connect [address]} and {@code /tokio disconnect}.
 * Only the singleplayer world's host can use them, cheats or not: a LAN guest
 * could otherwise point the host's world at their own socket.
 * {@code /tokio meteor [count]} and {@code /tokio laser [super [duper]]} also need
 * gamemaster permission, which Tokio's commands have, as the hazards break
 * blocks. So do {@code /tokio morph <mob> [seconds]} and {@code /tokio unmorph},
 * which turn the host into a mob and back.
 */
public final class TokioCommand {
	private TokioCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registries) {
		dispatcher.register(Commands.literal("tokio")
				.requires(TokioCommand::isHost)
				.executes(TokioCommand::status)
				.then(Commands.literal("connect")
						.executes(context -> connect(context, ""))
						.then(Commands.argument("address", StringArgumentType.greedyString())
								.executes(context -> connect(context, StringArgumentType.getString(context, "address")))))
				.then(Commands.literal("disconnect").executes(TokioCommand::disconnect))
				.then(Commands.literal("meteor")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> meteor(context, 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, Meteor.MAX_COUNT))
								.executes(context -> meteor(context, IntegerArgumentType.getInteger(context, "count")))))
				.then(Commands.literal("laser")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> laser(context, Laser.LASER, Chat.LASER))
						.then(Commands.literal("super")
								.executes(context -> laser(context, Laser.SUPER, Chat.SUPER_LASER))
								.then(Commands.literal("duper").executes(context -> laser(context, Laser.SUPER_DUPER, Chat.SUPER_DUPER_LASER)))))
				.then(Commands.literal("morph")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("mob", ResourceArgument.resource(registries, Registries.ENTITY_TYPE))
								.suggests((suggesting, builder) -> SharedSuggestionProvider.suggestResource(
										BuiltInRegistries.ENTITY_TYPE.stream().filter(Morph::canBecome).map(BuiltInRegistries.ENTITY_TYPE::getKey), builder))
								.executes(context -> morph(context, OptionalInt.empty()))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(1))
										.executes(context -> morph(context, OptionalInt.of(IntegerArgumentType.getInteger(context, "seconds")))))))
				.then(Commands.literal("unmorph")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(TokioCommand::unmorph)));
	}

	private static int morph(CommandContext<CommandSourceStack> context, OptionalInt seconds) throws CommandSyntaxException {
		EntityType<?> type = ResourceArgument.getResource(context, "mob", Registries.ENTITY_TYPE).value();
		ServerPlayer player = context.getSource().getPlayer();
		if (!Morph.start(player, type, seconds)) {
			context.getSource().sendFailure(Component.literal(Chat.notAMob(type.getDescription())));
			return 0;
		}
		// Forms wait only while a timed one is on, so one waiting is this one
		int waiting = Morph.waiting(player);
		String answer = waiting > 0 ? Chat.waitsFor(type.getDescription(), seconds, waiting - 1) : Chat.turnedInto(type.getDescription(), seconds);
		context.getSource().sendSuccess(() -> Component.literal(answer), false);
		return 1;
	}

	// Answers as run even for a player who wasn't a mob, so a Tokio action never fails on it
	private static int unmorph(CommandContext<CommandSourceStack> context) {
		Morph.end(context.getSource().getPlayer());
		context.getSource().sendSuccess(() -> Component.literal(Chat.TURNED_BACK), false);
		return 1;
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

	// They answer at once; the hazards play out over the next seconds
	private static int meteor(CommandContext<CommandSourceStack> context, int count) {
		Meteor.fall(context.getSource().getPlayer(), count);
		context.getSource().sendSuccess(() -> Component.literal(Chat.meteors(count)), false);
		return count;
	}

	private static int laser(CommandContext<CommandSourceStack> context, Laser.Kind kind, String text) {
		Laser.fire(context.getSource().getPlayer(), kind);
		context.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
