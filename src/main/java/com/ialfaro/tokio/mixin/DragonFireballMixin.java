package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Spawned;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A tagged dragon fireball's breath is tagged too, so it spares as the
 * fireball does. A Tokio dragon's breath needs nothing: its owner, the
 * dragon, is Tokio's.
 */
@Mixin(DragonFireball.class)
abstract class DragonFireballMixin {
	@ModifyArg(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private Entity tokio$tagBreath(Entity cloud) {
		if (Spawned.is((DragonFireball) (Object) this)) cloud.addTag(Spawned.TAG);
		return cloud;
	}
}
