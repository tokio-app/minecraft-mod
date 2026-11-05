package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tokio's blasts pass other players by, unhurt and unpushed: the mod's own,
 * and those of what Tokio's commands made, like TNT, creepers and a ghast's
 * fireball. A morphed player's fireballs and skulls spare the player too.
 */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
	@Redirect(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
	private boolean tokio$spareOthers(Entity entity, Explosion explosion) {
		return entity.ignoreExplosion(explosion) || Host.spares(explosion, entity);
	}
}
