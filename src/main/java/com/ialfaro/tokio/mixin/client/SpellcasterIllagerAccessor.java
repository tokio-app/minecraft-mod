package com.ialfaro.tokio.mixin.client;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The spell an illager casts, as a client reads it: its spells' enum is protected. */
@Mixin(SpellcasterIllager.class)
public interface SpellcasterIllagerAccessor {
	@Accessor("DATA_SPELL_CASTING_ID")
	static EntityDataAccessor<Byte> tokio$spell() {
		throw new AssertionError();
	}
}
