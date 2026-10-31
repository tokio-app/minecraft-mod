package com.ialfaro.tokio;

import com.ialfaro.tokio.game.Laser;
import com.ialfaro.tokio.game.Night;
import com.ialfaro.tokio.game.TokioCommand;
import com.ialfaro.tokio.game.WorldConnection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TokioCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STOPPING.register(WorldConnection::close);
		// Before the world saves, so it keeps no super laser's light or night
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Laser.stopAll());
		ServerLifecycleEvents.SERVER_STOPPING.register(Night::stop);
		ServerTickEvents.END_SERVER_TICK.register(Night::tick);
	}
}
