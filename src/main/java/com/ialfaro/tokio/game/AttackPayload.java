package com.ialfaro.tokio.game;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * From a client with the mod: its attack key went down or up. A client sends
 * it only while its player is a mob, and only to a world that has the mod.
 */
public record AttackPayload(boolean held) implements CustomPacketPayload {
	public static final Type<AttackPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tokio", "attack"));
	public static final StreamCodec<ByteBuf, AttackPayload> CODEC = ByteBufCodecs.BOOL.map(AttackPayload::new, AttackPayload::held);

	@Override
	public Type<AttackPayload> type() {
		return TYPE;
	}
}
