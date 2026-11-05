package com.ialfaro.tokio.mixin.client;

import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A guardian's beam target, which is private: a morphed player's copy aims its beam with it. */
@Mixin(Guardian.class)
public interface GuardianInvoker {
	@Invoker("setActiveAttackTarget")
	void tokio$setActiveAttackTarget(int entityId);
}
