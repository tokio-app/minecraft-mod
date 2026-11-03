package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tokio's evoker fangs, and those its evokers cast, bite no player but the host. */
@Mixin(EvokerFangs.class)
abstract class EvokerFangsMixin {
	@Inject(method = "dealDamageTo", at = @At("HEAD"), cancellable = true)
	private void tokio$spareOthers(LivingEntity entity, CallbackInfo ci) {
		if (Host.isOther(entity) && Host.tokios((EvokerFangs) (Object) this)) ci.cancel();
	}
}
