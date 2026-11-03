package com.ialfaro.tokio.mixin;

import com.ialfaro.tokio.game.Host;
import com.ialfaro.tokio.game.Spawned;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells {@link Spawned} of each new entity the world takes, passengers
 * included. Entities loaded with their chunk don't come this way. And the
 * mod's own blasts show, sound and push for the host alone.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	@Inject(method = "addEntity", at = @At("RETURN"))
	private void tokio$added(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) Spawned.added(entity);
	}

	// The blast's particles, sound and push, to each player near it
	@Redirect(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private void tokio$hostAlone(ServerGamePacketListenerImpl connection, Packet<?> packet) {
		if (Host.blasting() && Host.isOther(connection.player)) return;
		connection.send(packet);
	}
}
