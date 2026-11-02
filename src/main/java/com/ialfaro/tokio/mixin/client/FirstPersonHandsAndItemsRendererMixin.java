package com.ialfaro.tokio.mixin.client;

import com.ialfaro.tokio.game.Morph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No bare arm in first person while the player is a mob; what they hold still shows. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsAndItemsRendererMixin {
	// Both the empty hand and the hands holding a map draw through it
	@Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true)
	private void tokio$hideArm(CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Morph.look(minecraft.player) != null) ci.cancel();
	}
}
