package com.ialfaro.tokio;

import net.fabricmc.api.ModInitializer;
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
	}
}
