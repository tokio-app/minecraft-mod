package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Spawned;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells {@link Spawned} of each new entity the world takes, passengers
 * included. Entities loaded with their chunk don't come this way.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	@Inject(method = "addEntity", at = @At("RETURN"))
	private void tokio$added(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) Spawned.added(entity);
	}
}
