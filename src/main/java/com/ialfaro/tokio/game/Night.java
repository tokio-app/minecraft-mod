package com.ialfaro.tokio.game;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;

/**
 * The night super lasers bring: the sky runs on to midnight when the first one
 * starts, and on to the hour it was when the last one ends. Used on the server
 * thread; one world at a time runs in the integrated server.
 */
public final class Night {
	// The overworld's day, and its night (data/minecraft/timeline/day.json)
	private static final long DAY = 24000;
	private static final long MIDNIGHT = 18000;
	private static final long NIGHT_STARTS = 13000;
	private static final long NIGHT_ENDS = 23000;
	// How long the sky takes to run to midnight, or back to the day
	private static final int RUN_TICKS = 30;

	private static ServerClockManager clocks;
	private static Holder<WorldClock> clock;
	// The hour to come back to, or -1 when it was night already
	private static long hour = -1;
	// The run under way: from where, to where, how far along, and whether it's the dawn
	private static long from;
	private static long to;
	private static int ran = RUN_TICKS;
	private static boolean dawn;

	private Night() {
	}

	/** Runs the sky to midnight, unless it's night already. A dawn under way turns back. */
	static void fall(ServerLevel level) {
		Optional<Holder<WorldClock>> found = level.dimensionType().defaultClock();
		// The Nether keeps no time
		if (found.isEmpty()) return;
		clocks = level.getServer().clockManager();
		clock = found.get();
		long now = now();
		boolean dawning = dawn && ran < RUN_TICKS;
		long time = now % DAY;
		if (!dawning) {
			if (time >= NIGHT_STARTS && time < NIGHT_ENDS) return;
			hour = time;
		}
		run(now, next(now, MIDNIGHT), false);
	}

	/** Runs the sky on to the hour it was before night fell, if it fell. */
	static void lift() {
		if (clock == null || hour < 0) return;
		long now = now();
		run(now, next(now, hour), true);
	}

	public static void tick(MinecraftServer server) {
		if (ran >= RUN_TICKS || clocks == null) return;
		ran++;
		clocks.setTotalTicks(clock, from + (to - from) * ran / RUN_TICKS);
		if (ran == RUN_TICKS && dawn) hour = -1;
	}

	/** Brings the day back at once, before the world saves, and forgets it. */
	public static void stop(MinecraftServer server) {
		if (clocks != null && hour >= 0) clocks.setTotalTicks(clock, next(now(), hour));
		clocks = null;
		clock = null;
		hour = -1;
		ran = RUN_TICKS;
	}

	private static void run(long from, long to, boolean dawn) {
		Night.from = from;
		Night.to = to;
		Night.dawn = dawn;
		ran = 0;
	}

	private static long now() {
		return clocks.getInstance(clock).totalTicks();
	}

	// The first tick from now at that hour of the day
	private static long next(long now, long hour) {
		return now + Math.floorMod(hour - now, DAY);
	}
}
