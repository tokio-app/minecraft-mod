package com.ialfaro.tokio.game;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;

/**
 * The world's host, the singleplayer world's owner, is the only player what
 * Tokio's commands make, and the mod's hazards, reach. Other players in a LAN
 * world aren't hurt, pushed or targeted by them (the mixins ask here). Used
 * on the server thread.
 */
public final class Host {
	// One of the mod's own blasts under way (Hazard.blast)
	private static boolean blasting;

	private Host() {
	}

	/** Whether this is a player other than the host. False in a world without one, as in game tests. */
	public static boolean isOther(Entity entity) {
		if (!(entity instanceof Player player) || !(player.level() instanceof ServerLevel level)) return false;
		GameProfile owner = level.getServer().getSingleplayerProfile();
		return owner != null && !owner.id().equals(player.getUUID());
	}

	/** Whether Tokio's commands made this, or what it came from, like a mob's arrow. False for null. */
	public static boolean tokios(Entity entity) {
		if (entity == null) return false;
		return Spawned.is(entity) || entity instanceof TraceableEntity traceable && traceable.getOwner() instanceof Entity owner && Spawned.is(owner);
	}

	/** Whether the explosion is Tokio's: one of the mod's blasts, or set off by something Tokio's commands made. */
	public static boolean tokios(Explosion explosion) {
		return blasting || tokios(explosion.getDirectSourceEntity()) || tokios(explosion.getIndirectSourceEntity());
	}

	/** Whether one of the mod's own blasts is under way, which only the host sees and hears. */
	public static boolean blasting() {
		return blasting;
	}

	static void blast(Runnable explosion) {
		blasting = true;
		try {
			explosion.run();
		} finally {
			blasting = false;
		}
	}

	/**
	 * How many of that many particles a player's Particles setting shows: all,
	 * none on Minimal, and on Decreased each one at two in three, as the game
	 * drops its own.
	 */
	public static int particlesShown(int particles, ParticleStatus status, RandomSource random) {
		return switch (status) {
			case ALL -> particles;
			case MINIMAL -> 0;
			case DECREASED -> {
				int kept = 0;
				for (int i = 0; i < particles; i++) {
					if (random.nextInt(3) != 0) kept++;
				}
				yield kept;
			}
		};
	}
}
