package com.ialfaro.tokio.test;

import com.ialfaro.tokio.client.MorphLooks;
import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Morph;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * Morphs the host of a singleplayer world through /tokio and screenshots the
 * mobs (build/run/clientGameTest/screenshots), then turns them into every mob
 * and checks each is drawn as that mob, never as a player.
 */
public class MorphClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			// A clear stone floor, nothing in the camera's way, and no hurt: undead burn at noon
			host(server, "fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:stone");
			host(server, "fill ~-12 ~ ~-12 ~12 ~20 ~12 minecraft:air");
			host(server, "effect give @s minecraft:resistance infinite 4 true");
			host(server, "tp @s ~ ~ ~ 0 0");
			world.getConnection().waitForChunksRender();

			// The mobs alone, facing the camera, then from behind
			camera(context, CameraType.THIRD_PERSON_FRONT, true);
			shoot(context, server, "iron_golem");
			context.getInput().holdKeyFor(options -> options.keyUp, 6);
			context.takeScreenshot("morph-iron_golem-walking");
			host(server, "tp @s ~ ~ ~ 0 0");
			camera(context, CameraType.THIRD_PERSON_BACK, true);
			context.waitTicks(5);
			context.takeScreenshot("morph-iron_golem-back");
			camera(context, CameraType.THIRD_PERSON_FRONT, true);
			shoot(context, server, "chicken");
			shoot(context, server, "spider");
			host(server, "item replace entity @s weapon.mainhand with minecraft:iron_sword");
			host(server, "item replace entity @s armor.head with minecraft:iron_helmet");
			shoot(context, server, "zombie");
			shoot(context, server, "ghast");
			shoot(context, server, "slime");
			shoot(context, server, "warden");
			shoot(context, server, "ender_dragon");
			camera(context, CameraType.THIRD_PERSON_BACK, true);
			context.waitTicks(5);
			context.takeScreenshot("morph-ender_dragon-back");
			camera(context, CameraType.THIRD_PERSON_FRONT, false);
			shoot(context, server, "cod");
			// The cod's air runs out on land
			context.waitTicks(60);
			context.takeScreenshot("morph-cod-on-land");

			// No arm in first person, and what the hand holds still shows
			host(server, "item replace entity @s weapon.mainhand with minecraft:air");
			morph(context, server, "zombie");
			camera(context, CameraType.FIRST_PERSON, false);
			context.waitTicks(10);
			context.takeScreenshot("morph-first-person-empty-hand");
			host(server, "item replace entity @s weapon.mainhand with minecraft:iron_sword");
			context.waitTicks(30);
			context.takeScreenshot("morph-first-person-sword");

			camera(context, CameraType.THIRD_PERSON_FRONT, true);
			List<EntityType<?>> mobs = BuiltInRegistries.ENTITY_TYPE.stream().filter(Morph::canBecome).toList();
			for (EntityType<?> type : mobs) morph(context, server, BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
			expect(tokio(server, "tokio unmorph"), "Turned back.");
			context.waitFor(minecraft -> Morph.look(minecraft.player) == null);
			context.waitTicks(10);
			context.takeScreenshot("morph-turned-back");
			// A player's own first person with the sword, to compare
			camera(context, CameraType.FIRST_PERSON, false);
			context.waitTicks(10);
			context.takeScreenshot("morph-first-person-sword-as-a-player");
		}
	}

	private static void camera(ClientGameTestContext context, CameraType type, boolean hideGui) {
		context.runOnClient(minecraft -> {
			minecraft.options.setCameraType(type);
			if (minecraft.gui.hud.isHidden() != hideGui) minecraft.gui.hud.toggle();
		});
	}

	private static void shoot(ClientGameTestContext context, TestServerContext server, String mob) {
		morph(context, server, mob);
		// Let the camera settle at the new size
		context.waitTicks(10);
		context.takeScreenshot("morph-" + mob);
	}

	// Through the command, as Tokio sends it; it fails the test on another answer, or when the mob is never drawn
	private static void morph(ClientGameTestContext context, TestServerContext server, String mob) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(mob));
		expect(tokio(server, "tokio morph minecraft:" + mob), "Turned into " + type.getDescription().getString() + ".");
		context.waitFor(minecraft -> Morph.look(minecraft.player) == type, 100);
		context.waitFor(minecraft -> MorphLooks.drawnAs(minecraft.player) == type, 100);
	}

	// As Tokio runs it, as the host: /execute can't reach /tokio, which only the host sees
	private static CommandResult tokio(TestServerContext server, String command) {
		return server.computeOnServer(minecraft -> new CommandRunner(minecraft).run(command)).join();
	}

	// What Tokio gets back
	private static void expect(CommandResult answer, String text) {
		if (!answer.ok() || !answer.message().equals(text)) throw new AssertionError("Tokio got " + answer + ", not \"" + text + "\"");
	}

	private static void host(TestServerContext server, String command) {
		server.runCommand("execute as @p at @s run " + command);
	}
}
