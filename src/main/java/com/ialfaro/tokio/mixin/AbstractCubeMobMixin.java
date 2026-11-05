package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Spawned;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tokio's slimes, magma cubes and sulfur cubes hurt no one by touch in their grace: touching needs no target. */
@Mixin(AbstractCubeMob.class)
abstract class AbstractCubeMobMixin {
	@Inject(method = "dealDamage", at = @At("HEAD"), cancellable = true)
	private void tokio$grace(LivingEntity target, CallbackInfo ci) {
		if (Spawned.inGrace((AbstractCubeMob) (Object) this)) ci.cancel();
	}
}
