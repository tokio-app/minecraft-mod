package com.ialfaro.tokio.game;

import com.ialfaro.tokio.Tokio;
import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.connection.Commands;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * Runs Tokio's commands in the world as its host player, with gamemaster
 * permission, and keeps their feedback for Tokio instead of the chat.
 */
public final class CommandRunner implements Commands {
	public static final String NO_PLAYER = "The player isn't in the world yet.";

	private final MinecraftServer server;
	private final Supplier<ServerPlayer> player;

	/** Runs commands as the world's host. */
	public CommandRunner(MinecraftServer server) {
		this(server, () -> host(server));
	}

	/** Runs commands as the player {@code player} gives, on the server thread; null when there's none. */
	public CommandRunner(MinecraftServer server, Supplier<ServerPlayer> player) {
		this.server = server;
		this.player = player;
	}

	/** The singleplayer world's owner, or null while they aren't in it. */
	public static ServerPlayer host(MinecraftServer server) {
		GameProfile owner = server.getSingleplayerProfile();
		return owner == null ? null : server.getPlayerList().getPlayer(owner.id());
	}

	@Override
	public CompletableFuture<CommandResult> run(String commandLine) {
		CompletableFuture<CommandResult> result = new CompletableFuture<>();
		server.execute(() -> {
			try {
				result.complete(runHere(commandLine));
			} catch (RuntimeException e) {
				Tokio.LOGGER.error("Couldn't run /{}", commandLine, e);
				result.complete(new CommandResult(false, "The command failed."));
			}
		});
		return result;
	}

	private CommandResult runHere(String commandLine) {
		// A stopped server runs tasks on the caller's thread, which mustn't touch the world
		if (!server.isSameThread()) return new CommandResult(false, "The world is closing.");
		ServerPlayer player = this.player.get();
		if (player == null) return new CommandResult(false, NO_PLAYER);

		Output output = new Output();
		CommandSourceStack source = player.createCommandSourceStack()
				.withSource(output)
				.withPermission(LevelBasedPermissionSet.GAMEMASTER)
				.withCallback((success, value) -> output.success = success);
		server.getCommands().performPrefixedCommand(source, commandLine);
		return output.result();
	}

	/** Collects a command's feedback; nothing reaches the chat or the operators. */
	private static final class Output implements CommandSource {
		// /time set fails when the clock is already there, as when an action on
		// every gift sets night twice: the world is as Tokio asked, so it ran
		private static final Set<String> ALREADY = Set.of("commands.time.set.already_at_time_marker", "commands.time.set.already_at_time");

		private final List<String> lines = new ArrayList<>();
		private boolean success;
		private boolean already;

		@Override
		public void sendSystemMessage(Component message) {
			lines.add(message.getString());
			if (already(message)) already = true;
		}

		// Its key, whatever the game's language; a failure wraps it in red
		private static boolean already(Component message) {
			if (message.getContents() instanceof TranslatableContents contents && ALREADY.contains(contents.getKey())) return true;
			return message.getSiblings().stream().anyMatch(Output::already);
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}

		CommandResult result() {
			if (success || already) return new CommandResult(true, lines.isEmpty() ? "" : lines.getLast());
			// The first line is the error; a parse error's second shows where it is
			return new CommandResult(false, lines.isEmpty() ? "The command failed." : lines.getFirst());
		}
	}
}
