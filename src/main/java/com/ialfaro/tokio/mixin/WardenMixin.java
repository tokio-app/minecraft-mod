package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import com.ialfaro.tokio.game.Spawned;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tokio's Warden gets angry at no player but the host: its anger picks targets apart from Mob.canAttack. */
@Mixin(Warden.class)
abstract class WardenMixin {
	@Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
	private void tokio$hostOnly(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Host.isOther(entity) && Spawned.is((Warden) (Object) this)) cir.setReturnValue(false);
	}
}
