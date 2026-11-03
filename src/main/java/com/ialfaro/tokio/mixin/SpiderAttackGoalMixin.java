package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Spawned;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Spiders Tokio spawns keep attacking in daylight, where the game has them give up. */
@Mixin(targets = "net.minecraft.world.entity.monster.spider.Spider$SpiderAttackGoal")
abstract class SpiderAttackGoalMixin {
	@Redirect(method = "canContinueToUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getLightLevelDependentMagicValue()F"))
	private float tokio$keepAttacking(PathfinderMob spider) {
		return Spawned.is(spider) ? 0 : spider.getLightLevelDependentMagicValue();
	}
}
