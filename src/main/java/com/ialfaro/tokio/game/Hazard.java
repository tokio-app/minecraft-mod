package com.ialfaro.tokio.game;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * A hazard that plays out over a few seconds as a glowing block display, run
 * by its own tick on the server. It's its host's alone: only their client gets
 * the display, its particles and its sounds, and its blasts hurt and push no
 * other player, though the blocks they break are gone for everyone. It's never
 * saved, so a world closed or a chunk unloaded midway keeps no stray meteor or
 * beam.
 */
abstract class Hazard extends Display.BlockDisplay {
	// The player it's aimed at
	final ServerPlayer host;

	Hazard(ServerPlayer host) {
		super(EntityTypes.BLOCK_DISPLAY, host.level());
		this.host = host;
		setBrightnessOverride(Brightness.FULL_BRIGHT);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	// Other players' clients never get it
	@Override
	public boolean broadcastToPlayer(ServerPlayer player) {
		return isHost(player);
	}

	// By id: a host who respawned is a new player
	boolean isHost(ServerPlayer player) {
		return player.getUUID().equals(host.getUUID());
	}

	ServerLevel serverLevel() {
		return (ServerLevel) level();
	}

	/**
	 * Where something falling on the spot lands: the ground or water under it,
	 * or the spot itself over the void. On the ground, that's the spot.
	 */
	static Vec3 groundUnder(ServerLevel level, Vec3 spot) {
		// From a little up, so a spot on a slab's top still finds the slab
		Vec3 from = spot.add(0, 0.1, 0);
		Vec3 to = new Vec3(spot.x, level.getMinY(), spot.z);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? spot : hit.getLocation();
	}

	/** Whether the hazards may break blocks: like TNT, unless tnt_explodes is off. */
	boolean breaks() {
		return serverLevel().getGameRules().get(GameRules.TNT_EXPLODES);
	}

	/** Breaks blocks and starts fires like TNT, or only hurts while tnt_explodes is off. Only the host sees, hears and feels it. */
	void blast(Vec3 at, float power) {
		boolean breaks = breaks();
		Host.blast(() -> serverLevel().explode(null, at.x, at.y, at.z, power, breaks, breaks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE));
	}

	/**
	 * Particles for the host, as their Particles setting shows them, from up to
	 * 512 blocks away: a meteor's trail starts far up. With no count, the one
	 * particle goes the spread's way at the speed.
	 */
	void particles(ParticleOptions particle, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed) {
		// The game tests' mock players have no connection
		if (host.connection == null) return;
		int shown = Host.particlesShown(Math.max(count, 1), host.clientInformation().particleStatus(), random);
		if (shown > 0) serverLevel().sendParticles(host, particle, true, false, x, y, z, count == 0 ? 0 : shown, xDist, yDist, zDist, speed);
	}

	/** A sound there, for the host. */
	void sound(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
		if (host.connection == null) return;
		host.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE, x, y, z, volume, pitch, random.nextLong()));
	}
}
