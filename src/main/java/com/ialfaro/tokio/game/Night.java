package com.ialfaro.tokio.game;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;

/**
 * The night super lasers bring, on their host's screen alone: the sky runs on
 * to midnight when the first one starts, and on to the world's own hour when
 * the last one ends. The world's clock never changes, so nothing else does:
 * other players' sky, mob spawning, the time the world saves. Each super laser
 * holds it a tick at a time, so one that stops ticking, left behind out of the
 * world's reach, lets the day come back. Used on the server thread; one world
 * at a time runs in the integrated server.
 */
public final class Night {
	// The overworld's day, and its night (data/minecraft/timeline/day.json)
	private static final long DAY = 24000;
	private static final long MIDNIGHT = 18000;
	private static final long NIGHT_STARTS = 13000;
	private static final long NIGHT_ENDS = 23000;
	// How long the sky takes to run to midnight, or back to the day
	private static final int RUN_TICKS = 30;
	// A respawn, a trip to another dimension or a clock someone changed sends
	// the host the world's own: the night goes again this often
	private static final int RESEND_TICKS = 20;

	private static ServerPlayer host;
	private static Holder<WorldClock> clock;
	// How far ahead of the world's clock the host's sky is, in ticks
	private static long ahead;
	// The run under way: from how far ahead, to how far, how far along, and whether it's the dawn
	private static long from;
	private static long to;
	private static int ran = RUN_TICKS;
	private static boolean dawn;
	// The server tick a super laser last held the night at, or -1
	private static long heldAt = -1;

	private Night() {
	}

	/** Keeps it night on the player's screen for a super laser aimed at them, this tick. The first hold brings it. */
	public static void hold(ServerPlayer player) {
		if (heldAt < 0) fall(player);
		heldAt = player.level().getServer().getTickCount();
	}

	/** How far ahead of the world's clock the host's sky is, in ticks: 0 while it shows the world's hour. */
	public static long ahead() {
		return ahead;
	}

	// Runs the host's sky to midnight, unless it's night there already. A dawn under way turns back.
	private static void fall(ServerPlayer player) {
		Optional<Holder<WorldClock>> found = player.level().dimensionType().defaultClock();
		// The Nether keeps no time
		if (found.isEmpty()) return;
		host = player;
		clock = found.get();
		long seen = world(player.level().getServer()) + ahead;
		boolean dawning = dawn && ran < RUN_TICKS;
		long time = seen % DAY;
		if (!dawning && time >= NIGHT_STARTS && time < NIGHT_ENDS) return;
		run(ahead, ahead + Math.floorMod(MIDNIGHT - seen, DAY), false);
	}

	// Runs the host's sky on to the world's own hour, a whole day or so ahead
	private static void lift() {
		if (clock == null || ahead == 0) return;
		run(ahead, Math.ceilDiv(ahead, DAY) * DAY, true);
	}

	/** After the super lasers' ticks: the host's sky runs, and the day comes back once none held the night. */
	public static void tick(MinecraftServer server) {
		if (heldAt >= 0 && heldAt < server.getTickCount()) {
			heldAt = -1;
			lift();
		}
		if (clock == null) return;
		if (ran < RUN_TICKS) {
			ran++;
			ahead = from + (to - from) * ran / RUN_TICKS;
			// Whole days ahead the sky looks the world's: its own clock again, the days counted the same
			if (ran == RUN_TICKS && dawn) ahead = 0;
			send(server);
		} else if (ahead != 0 && server.getTickCount() % RESEND_TICKS == 0) {
			send(server);
		}
		if (ran >= RUN_TICKS && ahead == 0) forget();
	}

	/** Forgets the night as the world closes: the world's clock never changed, so there's no day to bring back. */
	public static void stop(MinecraftServer server) {
		forget();
		heldAt = -1;
	}

	// The world's clock, so far ahead, to the host: their client runs it on from there
	private static void send(MinecraftServer server) {
		// The game tests' mock players have no connection
		if (host.connection == null) return;
		ClockNetworkState world = server.clockManager().getInstance(clock).packNetworkState(server);
		ClockNetworkState seen = new ClockNetworkState(world.totalTicks() + ahead, world.partialTick(), world.rate());
		host.connection.send(new ClientboundSetTimePacket(server.overworld().getGameTime(), Map.of(clock, seen)));
	}

	private static void run(long from, long to, boolean dawn) {
		Night.from = from;
		Night.to = to;
		Night.dawn = dawn;
		ran = 0;
	}

	private static void forget() {
		host = null;
		clock = null;
		ahead = 0;
		ran = RUN_TICKS;
		dawn = false;
	}

	private static long world(MinecraftServer server) {
		return server.clockManager().getInstance(clock).totalTicks();
	}
}
