package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Morph;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A morphed player is the mob's size in every pose but sleeping and dying.
 * On both sides: the player moves on their own client.
 */
@Mixin(Avatar.class)
abstract class AvatarMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void tokio$mobSize(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (!((Object) this instanceof Player player) || pose == Pose.SLEEPING || pose == Pose.DYING) return;
		EntityType<?> look = Morph.look(player);
		if (look == null) return;
		Mob mob = Morph.instance(player.level(), look);
		if (mob != null) cir.setReturnValue(mob.getDimensions(Pose.STANDING));
	}
}
