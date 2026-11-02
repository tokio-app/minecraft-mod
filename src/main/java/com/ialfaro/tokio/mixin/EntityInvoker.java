package com.ialfaro.tokio.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A mob's own steps, which are protected. */
@Mixin(Entity.class)
public interface EntityInvoker {
	@Invoker("playStepSound")
	void tokio$playStepSound(BlockPos pos, BlockState state);
}
