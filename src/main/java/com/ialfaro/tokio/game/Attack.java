package com.ialfaro.tokio.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A mob's attack, for a player morphed into it ({@link Attacks}). While the
 * attack key is held, one starts as soon as the last one's cooldown is over:
 * it winds up, fires its shots, then waits. Times are in ticks. Used on the
 * server thread.
 */
public interface Attack {
	/** Ticks from the start to the first shot, while the mob charges: a ghast's open mouth, a creeper's fuse. */
	default int windup() {
		return 0;
	}

	/** How many shots it fires, {@link #gap()} ticks apart, like a blaze's three fireballs. */
	default int shots() {
		return 1;
	}

	default int gap() {
		return 0;
	}

	/** Ticks after its last shot before the next attack can start. */
	int cooldown();

	/**
	 * As the attack starts, before its windup: what it's aimed at, kept until it
	 * fires, or null. It plays the windup's sound. An attack that
	 * {@link #needsTarget()} doesn't start without one, and waits no cooldown.
	 */
	default LivingEntity start(ServerPlayer player) {
		return null;
	}

	default boolean needsTarget() {
		return false;
	}

	/** Each tick of the windup: false calls the attack off, as a guardian's beam ends when it loses sight of its target. */
	default boolean holds(ServerPlayer player, LivingEntity target) {
		return true;
	}

	/** Fires one shot, where the player looks now, at what {@link #start} aimed at, or null. */
	void fire(ServerPlayer player, LivingEntity target);
}
