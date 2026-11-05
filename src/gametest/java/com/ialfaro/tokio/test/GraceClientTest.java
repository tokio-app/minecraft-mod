package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Spawned;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/**
 * Twenty chicken jockeys sent as Tokio sends a Spawn mob step of 20, one every
 * 5 ticks at the host's feet: none hurts the host in its grace, then they
 * attack. The screenshots land in build/run/clientGameTest/screenshots.
 */
public class GraceClientTest implements FabricClientGameTest {
	// Tokio's Java chicken jockey
	private static final String JOCKEY = "summon minecraft:chicken ~ ~ ~ {Passengers:[{id:\"minecraft:zombie\",IsBaby:1b}]}";
	// Tokio's gap between the mobs of a step, 250 ms
	private static final int GAP_TICKS = 5;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty normal");
			server.runCommand("time set noon");
			// Enough to watch twenty of them at it: 100 health and 80% less damage
			server.runCommand("attribute @p minecraft:max_health base set 100");
			server.runCommand("effect give @p minecraft:resistance infinite 3 true");
			server.runCommand("effect give @p minecraft:instant_health 1 10 true");
			// The front camera sits where the player looks: looking up, it's above
			// them, looking down at the crowd around their feet
			server.runCommand("tp @p ~ ~ ~ 0 -50");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			float full = health(server);

			for (int jockey = 1; jockey <= 20; jockey++) {
				ok(tokio(server, JOCKEY));
				context.waitTicks(GAP_TICKS);
				// The first one 5 ticks short of the end of its grace
				if (jockey == Spawned.GRACE_TICKS / GAP_TICKS - 1) {
					if (health(server) < full) throw new AssertionError("A jockey hit the host in its grace");
					context.takeScreenshot("grace-first-jockeys-waiting");
				}
			}
			context.takeScreenshot("grace-twenty-jockeys-the-first-attacking");
			// The last one's grace is over too
			context.waitTicks(Spawned.GRACE_TICKS + 20);
			context.takeScreenshot("grace-twenty-jockeys-attacking");
			if (health(server) >= full) throw new AssertionError("The jockeys never hit the host");
		}
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(minecraft -> CommandRunner.host(minecraft).getHealth());
	}

	// As Tokio runs it, as the host
	private static CommandResult tokio(TestServerContext server, String command) {
		return server.computeOnServer(minecraft -> new CommandRunner(minecraft).run(command)).join();
	}

	private static void ok(CommandResult answer) {
		if (!answer.ok()) throw new AssertionError("Tokio got " + answer);
	}
}
