package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Tokio's lightning strikes no player but the host; the fire it starts is the world's. */
@Mixin(LightningBolt.class)
abstract class LightningBoltMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V"))
	private void tokio$spareOthers(Entity entity, ServerLevel level, LightningBolt bolt) {
		if (Host.isOther(entity) && Host.tokios(bolt)) return;
		entity.thunderHit(level, bolt);
	}
}
