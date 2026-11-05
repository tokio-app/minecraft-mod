package com.ialfaro.tokio.mixin.client;

import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A blaze's charge, which is private: a morphed player's copy burns while their attack winds up. */
@Mixin(Blaze.class)
public interface BlazeInvoker {
	@Invoker("setCharged")
	void tokio$setCharged(boolean charged);
}
