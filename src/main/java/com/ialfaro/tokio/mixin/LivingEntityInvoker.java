package com.ialfaro.tokio.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A mob's own hurt and death sounds, which are protected. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("getHurtSound")
	SoundEvent tokio$getHurtSound(DamageSource source);

	@Invoker("getDeathSound")
	SoundEvent tokio$getDeathSound();
}
