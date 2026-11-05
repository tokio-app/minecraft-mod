package com.ialfaro.tokio.game;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** What a morphed player's melee hit does on top of its damage, as the mob's own bite or punch does. */
public final class Hits {
	private Hits() {
	}

	/** After anything takes damage (Fabric's {@code AFTER_DAMAGE}). */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damage, boolean blocked) {
	}
}
