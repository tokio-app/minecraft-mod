package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Tokio's clouds, like the dragon's breath, reach no player but the host; a morphed dragon's spare the player too. */
@Mixin(AreaEffectCloud.class)
abstract class AreaEffectCloudMixin {
	@Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByPotions()Z"))
	private boolean tokio$spareOthers(LivingEntity entity) {
		return entity.isAffectedByPotions() && !Host.spares((AreaEffectCloud) (Object) this, entity);
	}
}
