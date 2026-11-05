package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tokio's splash potions, and those its witches throw, splash no player but
 * the host; a morphed witch's spare the player too. The splash reaches
 * everything around, not only what the potion hits.
 */
@Mixin(ThrownSplashPotion.class)
abstract class ThrownSplashPotionMixin {
	@Redirect(method = "onHitAsPotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByPotions()Z"))
	private boolean tokio$spareOthers(LivingEntity entity) {
		return entity.isAffectedByPotions() && !Host.spares((ThrownSplashPotion) (Object) this, entity);
	}
}
