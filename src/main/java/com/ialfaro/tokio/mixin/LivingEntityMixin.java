package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Morph;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player who's a spider climbs the walls they walk into, as a spider does. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void tokio$climbWalls(CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof Player player) || !player.horizontalCollision) return;
		EntityType<?> look = Morph.look(player);
		if (look == EntityTypes.SPIDER || look == EntityTypes.CAVE_SPIDER) cir.setReturnValue(true);
	}
}
