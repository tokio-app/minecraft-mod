package com.ialfaro.tokio.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.EntityTypes;

/** Draws the lasers' beams as beacon beams, on this client only. */
public final class Beams implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(EntityTypes.BLOCK_DISPLAY, LaserRenderer::new);
	}
}
