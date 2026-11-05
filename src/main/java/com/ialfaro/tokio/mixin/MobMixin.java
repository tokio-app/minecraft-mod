package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import com.ialfaro.tokio.game.Spawned;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mobs Tokio spawns don't burn in daylight: one on fire sets the player
 * alight with each hit. Fire and lava still burn them. They go after no one in
 * their grace, then the hostile ones go after the host, and never after
 * another player.
 */
@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
	private void tokio$noSunBurn(CallbackInfo ci) {
		if (Spawned.is((Mob) (Object) this)) ci.cancel();
	}

	// Every target goes through it, the goals', the brains' and setTarget's (asValidTarget)
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void tokio$hostOnly(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		Mob mob = (Mob) (Object) this;
		if (Spawned.is(mob) && (Host.isOther(target) || Spawned.inGrace(mob))) cir.setReturnValue(false);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void tokio$afterGrace(CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (!mob.level().isClientSide()) Spawned.ticked(mob);
	}
}
