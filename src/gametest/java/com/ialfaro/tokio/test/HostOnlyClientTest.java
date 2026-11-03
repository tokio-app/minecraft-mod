package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.clock.WorldClock;

/**
 * What the host's own game shows: a super laser's night on their screen while
 * the world's clock stays at noon, the day back after it on the world's clock,
 * and the hazards' particles as their Particles setting has them. The night's
 * screenshot lands in build/run/clientGameTest/screenshots.
 */
public class HostOnlyClientTest implements FabricClientGameTest {
	// "SQ 180 NR 1 T 181": T is every particle there is
	private static final Pattern PARTICLES = Pattern.compile("T (\\d+)");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			// The blasts hurt the host, the only one they hurt
			server.runCommand("execute as @p run effect give @s minecraft:resistance infinite 4 true");

			ok(tokio(server, "tokio laser super"));
			// Past the sky's run to midnight
			context.waitTicks(50);
			long seen = context.computeOnClient(HostOnlyClientTest::clientTicks);
			long time = server.computeOnServer(HostOnlyClientTest::worldTicks);
			if (!night(seen)) throw new AssertionError("It isn't night on the host's screen: " + seen % 24000);
			if (night(time)) throw new AssertionError("The world's clock ran to the night: " + time % 24000);
			context.takeScreenshot("host-only-super-laser-night");
			// The laser, its smoke and the dawn are over: the world's own clock, the day's count and all
			context.waitTicks(220);
			seen = context.computeOnClient(HostOnlyClientTest::clientTicks);
			time = server.computeOnServer(HostOnlyClientTest::worldTicks);
			if (Math.abs(seen - time) > 40) throw new AssertionError("The host's sky isn't the world's again: " + seen + ", not about " + time);

			// The meteors' trails, on Minimal and on All
			int minimal = meteorParticles(context, server, ParticleStatus.MINIMAL);
			int all = meteorParticles(context, server, ParticleStatus.ALL);
			if (all < 100 || minimal * 4 > all) throw new AssertionError("Minimal shows " + minimal + " particles, All " + all);
		}
	}

	// The particles there are 20 ticks into a shower, with the player's setting at that; the meteors land after
	private static int meteorParticles(ClientGameTestContext context, TestServerContext server, ParticleStatus setting) {
		// Saving the options tells the server, as the options screen does
		context.runOnClient(minecraft -> {
			minecraft.options.particles().set(setting);
			minecraft.options.save();
		});
		context.waitTicks(5);
		// Only the shower's: the super laser's smoke lives for 15 seconds, made while the setting was All
		context.runOnClient(minecraft -> minecraft.particleEngine.clearParticles());
		ok(tokio(server, "tokio meteor 3"));
		context.waitTicks(20);
		int count = context.computeOnClient(minecraft -> {
			Matcher total = PARTICLES.matcher(minecraft.particleEngine.countParticles());
			return total.find() ? Integer.parseInt(total.group(1)) : 0;
		});
		context.waitTicks(150);
		return count;
	}

	private static boolean night(long ticks) {
		long hour = ticks % 24000;
		return hour >= 13000 && hour < 23000;
	}

	private static long clientTicks(Minecraft minecraft) {
		Holder<WorldClock> clock = minecraft.level.dimensionType().defaultClock().orElseThrow();
		return minecraft.level.clockManager().getInstance(clock).totalTicks();
	}

	private static long worldTicks(MinecraftServer server) {
		Holder<WorldClock> clock = server.overworld().dimensionType().defaultClock().orElseThrow();
		return server.clockManager().getInstance(clock).totalTicks();
	}

	// As Tokio runs it, as the host: /execute can't reach /tokio, which only the host sees
	private static CommandResult tokio(TestServerContext server, String command) {
		return server.computeOnServer(minecraft -> new CommandRunner(minecraft).run(command)).join();
	}

	private static void ok(CommandResult answer) {
		if (!answer.ok()) throw new AssertionError("Tokio got " + answer);
	}
}
