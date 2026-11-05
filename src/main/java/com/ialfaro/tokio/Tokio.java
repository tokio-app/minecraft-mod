package com.ialfaro.tokio;

import com.ialfaro.tokio.game.AttackPayload;
import com.ialfaro.tokio.game.Attacks;
import com.ialfaro.tokio.game.Hits;
import com.ialfaro.tokio.game.Laser;
import com.ialfaro.tokio.game.Morph;
import com.ialfaro.tokio.game.Night;
import com.ialfaro.tokio.game.TokioCommand;
import com.ialfaro.tokio.game.WorldConnection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects the world the player opens to Tokio, which sends the commands its
 * game actions run. Single-player only: the world's host connects it with
 * {@code /tokio connect}.
 */
public class Tokio implements ModInitializer {
	public static final String MOD_ID = "tokio";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Registers the morph's look and its attack's windup now: a client reports the synced attachments it knows when it joins
		Morph.LOOK.identifier();
		Attacks.WINDUP.identifier();
		// A morphed player's attack key, from their client; the handler runs on the server thread
		PayloadTypeRegistry.serverboundPlay().register(AttackPayload.TYPE, AttackPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AttackPayload.TYPE, (payload, context) -> Attacks.held(context.player(), payload.held()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Hits::afterDamage);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TokioCommand.register(dispatcher, context));
		ServerLifecycleEvents.SERVER_STOPPING.register(WorldConnection::close);
		// Before the world saves, so it keeps no super laser's light and no morph
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Laser.stopAll());
		ServerLifecycleEvents.SERVER_STOPPING.register(Night::stop);
		ServerLifecycleEvents.SERVER_STOPPING.register(Morph::stopAll);
		ServerTickEvents.END_SERVER_TICK.register(Night::tick);
		ServerTickEvents.END_SERVER_TICK.register(Morph::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register(Morph::respawned);
		ServerPlayerEvents.JOIN.register(Morph::joined);
	}
}
