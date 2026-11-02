package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Morph;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A morphed player sounds like the mob: its hurt, death and step sounds. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void tokio$hurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		Mob mob = mob();
		if (mob != null) cir.setReturnValue(((LivingEntityInvoker) mob).tokio$getHurtSound(source));
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void tokio$deathSound(CallbackInfoReturnable<SoundEvent> cir) {
		Mob mob = mob();
		if (mob != null) cir.setReturnValue(((LivingEntityInvoker) mob).tokio$getDeathSound());
	}

	// The client's own steps go quiet; the server has the mob step for everyone, the player too
	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void tokio$stepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
		Player player = (Player) (Object) this;
		EntityType<?> look = Morph.look(player);
		if (look == null) return;
		ci.cancel();
		if (!player.level().isClientSide()) Morph.playStepSound(player, look, pos, state);
	}

	private Mob mob() {
		Player player = (Player) (Object) this;
		EntityType<?> look = Morph.look(player);
		return look == null ? null : Morph.instance(player.level(), look);
	}
}
