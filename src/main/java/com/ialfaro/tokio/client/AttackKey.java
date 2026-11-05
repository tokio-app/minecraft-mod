package com.ialfaro.tokio.client;

import com.ialfaro.tokio.game.AttackPayload;
import com.ialfaro.tokio.game.Attacks;
import com.ialfaro.tokio.game.Morph;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * The mob attack key (R by default): while a morphed player holds it, their
 * mob attacks ({@link Attacks}). The server hears when it goes down or up, only
 * while the player is a mob with an attack and no screen is open, and only in a
 * world with the mod.
 */
public final class AttackKey implements ClientModInitializer {
	private static KeyMapping key;
	// What the server was last told
	private static boolean sent;
	private static ClientLevel lastLevel;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tokio", "tokio"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tokio.attack", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, category));
		ClientTickEvents.END_CLIENT_TICK.register(AttackKey::tick);
	}

	/** The key, for the client game test. */
	public static KeyMapping key() {
		return key;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level != lastLevel) {
			lastLevel = minecraft.level;
			// Another dimension keeps the server's player, so let go there; another world starts with nothing held
			if (sent && ClientPlayNetworking.canSend(AttackPayload.TYPE)) ClientPlayNetworking.send(new AttackPayload(false));
			sent = false;
		}
		EntityType<?> look = minecraft.player != null ? Morph.look(minecraft.player) : null;
		boolean held = key.isDown() && look != null && Attacks.of(look) != null && minecraft.gui.screen() == null;
		if (held != sent && ClientPlayNetworking.canSend(AttackPayload.TYPE)) {
			ClientPlayNetworking.send(new AttackPayload(held));
			sent = held;
		}
	}
}
