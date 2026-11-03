package com.ialfaro.tokio.game;

import java.util.ArrayList;
import java.util.List;
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
 * tag, saved with it, and the hostile mobs go after the world's host at once.
 * Tagged mobs never burn in daylight ({@code MobMixin}). Used on the server
 * thread.
 */
public final class Spawned {
	public static final String TAG = "tokio";

	// What the command under way added (ServerLevelMixin), or null outside Tokio's commands
	private static List<Entity> added;

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
			if (entity instanceof Mob mob && mob instanceof Enemy) attack(mob, host);
		}
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
