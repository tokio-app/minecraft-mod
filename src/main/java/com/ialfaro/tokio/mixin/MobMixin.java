package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Spawned;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mobs Tokio spawns don't burn in daylight: one on fire sets the player
 * alight with each hit. Fire and lava still burn them.
 */
@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
	private void tokio$noSunBurn(CallbackInfo ci) {
		if (Spawned.is((Mob) (Object) this)) ci.cancel();
	}
}
