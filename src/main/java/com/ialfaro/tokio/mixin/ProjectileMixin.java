package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What Tokio's mobs shoot at the host, and projectiles Tokio's commands made,
 * fly through other players; a morphed player's shots fly through them too,
 * and never hit the player. Every projectile's own check calls this one.
 */
@Mixin(Projectile.class)
abstract class ProjectileMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void tokio$passOthers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Host.spares((Projectile) (Object) this, entity)) cir.setReturnValue(false);
	}
}
