package com.ialfaro.tokio.game;

import net.minecraft.server.level.ServerLevel;
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
 * by its own tick on the server. Clients get a plain block display, so LAN
 * guests without the mod see it too. It's never saved, so a world closed or a
 * chunk unloaded midway keeps no stray meteor or beam.
 */
abstract class Hazard extends Display.BlockDisplay {
	Hazard(ServerLevel level) {
		super(EntityTypes.BLOCK_DISPLAY, level);
		setBrightnessOverride(Brightness.FULL_BRIGHT);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
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

	/** Breaks blocks and starts fires like TNT, or only hurts while tnt_explodes is off. */
	void blast(Vec3 at, float power) {
		boolean breaks = breaks();
		serverLevel().explode(null, at.x, at.y, at.z, power, breaks, breaks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
	}
}
