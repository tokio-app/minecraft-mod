package com.ialfaro.tokio.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * What Tokio's commands add to the world: each entity gets the {@link #TAG}
 * tag, saved with it. Tagged mobs never burn in daylight ({@code MobMixin}),
 * and go after no one for their first {@link #GRACE_TICKS}; then the hostile
 * ones go after the world's host. Used on the server thread.
 */
public final class Spawned {
	public static final String TAG = "tokio";

	/**
	 * How long Tokio's mobs leave everyone alone after they appear, in ticks
	 * (1 second, the user's call): they appear at the host's feet, so without
	 * it a crowd's first hits come at once and can kill before the host sees
	 * them. A mob that loads with its chunk gets it again, as its age starts
	 * over.
	 */
	public static final int GRACE_TICKS = 20;

	// What the command under way added (ServerLevelMixin), or null outside Tokio's commands
	private static List<Entity> added;

	// Tokio's hostile mobs in their grace, and the host each goes after once it's
	// over. Weak, so a mob that leaves the world isn't kept.
	private static final Map<Mob, ServerPlayer> waiting = new WeakHashMap<>();

	private Spawned() {
	}

	/** Runs one of Tokio's commands as {@code host}, then marks what it spawned. */
	static void during(ServerPlayer host, Runnable command) {
		List<Entity> entities = new ArrayList<>();
		added = entities;
		try {
			command.run();
		} finally {
			added = null;
		}
		for (Entity entity : entities) {
			if (entity.isRemoved()) continue;
			entity.addTag(TAG);
			if (entity instanceof Mob mob && mob instanceof Enemy) waiting.put(mob, host);
		}
	}

	/** Whether the mob is Tokio's and still in its grace, when it can't go after anyone. */
	public static boolean inGrace(Mob mob) {
		return mob.tickCount < GRACE_TICKS && is(mob);
	}

	/** After a mob's tick on the server: Tokio's hostile ones go after their host once their grace is over. */
	public static void ticked(Mob mob) {
		if (waiting.isEmpty() || mob.tickCount < GRACE_TICKS) return;
		ServerPlayer host = waiting.remove(mob);
		if (host != null && host.isAlive() && !host.isRemoved()) attack(mob, host);
	}

	/** An entity entered a world; it's Tokio's while one of its commands runs. */
	public static void added(Entity entity) {
		if (added != null) added.add(entity);
	}

	public static boolean is(Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	private static void attack(Mob mob, ServerPlayer host) {
		mob.setTarget(host);
		// Neutral monsters (endermen, zombified piglins) drop a target they aren't angry at
		if (mob instanceof NeutralMob neutral) {
			neutral.setPersistentAngerTarget(EntityReference.of(host));
			neutral.startPersistentAngerTimer();
		}
		if (mob instanceof Warden warden) {
			// As a hit does; its anger alone would make it roar first
			warden.increaseAngerAt(host, AngerLevel.ANGRY.getMinimumAnger() + 20, false);
			warden.setAttackTarget(host);
			return;
		}
		// Brain-driven mobs (piglins, hoglins, zoglins, the Breeze, the Creaking) keep their
		// target in memory; a brain that doesn't have one skips it. Piglins stay on whoever
		// they're angry at, gold or not
		mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, host);
		mob.getBrain().setMemory(MemoryModuleType.ANGRY_AT, host.getUUID());
	}
}
