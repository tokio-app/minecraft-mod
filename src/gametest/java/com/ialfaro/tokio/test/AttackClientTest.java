package com.ialfaro.tokio.test;

import com.ialfaro.tokio.client.AttackKey;
import com.ialfaro.tokio.client.MorphLooks;
import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.Attacks;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Morph;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;

/**
 * A morphed host of a singleplayer world holds the attack key: each family's
 * attack fires at a still iron golem in front, the mob shows its windup, and
 * the host's own creeper blast spares them. The screenshots land in
 * build/run/clientGameTest/screenshots.
 */
public class AttackClientTest implements FabricClientGameTest {
	private static final String TARGET = "attack_target";
	// How high the host stands to look down at the golem
	private static final int PILLAR = 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			host(server, "effect give @s minecraft:fire_resistance infinite 0 true");
			BlockPos at = server.computeOnServer(minecraft -> CommandRunner.host(minecraft).blockPosition());

			// The skulls fly from the wither's head
			arena(context, server, at, "wither", 8, true);
			context.getInput().holdKey(AttackKey.key());
			until(context, server, minecraft -> !owned(minecraft, WitherSkull.class).isEmpty(), 60, "The wither shot no skull of the host's");
			aside(context, server);
			context.takeScreenshot("attack-wither-skulls");

			// Its mouth opens, then the fireball
			arena(context, server, at, "ghast", 10, true);
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.getInput().holdKey(AttackKey.key());
			windup(context);
			context.waitTicks(5);
			context.takeScreenshot("attack-ghast-charging");
			aside(context, server);
			until(context, server, minecraft -> !owned(minecraft, LargeFireball.class).isEmpty(), 60, "The ghast shot no fireball of the host's");
			context.waitTicks(3);
			context.takeScreenshot("attack-ghast-fireball");

			// It burns as it charges, then fires a burst
			arena(context, server, at, "blaze", 8, true);
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.getInput().holdKey(AttackKey.key());
			windup(context);
			context.waitTicks(5);
			context.takeScreenshot("attack-blaze-charged");
			aside(context, server);
			until(context, server, minecraft -> !owned(minecraft, SmallFireball.class).isEmpty(), 60, "The blaze shot no fireball of the host's");
			context.waitTicks(4);
			context.takeScreenshot("attack-blaze-burst");

			// Its chest opens before the boom, which reaches the golem
			arena(context, server, at, "warden", 8, true);
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.getInput().holdKey(AttackKey.key());
			windup(context);
			context.waitTicks(15);
			context.takeScreenshot("attack-warden-boom");
			aside(context, server);
			until(context, server, AttackClientTest::targetHurt, 100, "The Warden's boom never hurt the golem");
			context.takeScreenshot("attack-warden-boom-hit");

			// The beam reaches the golem as it charges, then it hurts
			arena(context, server, at, "guardian", 6, true);
			context.getInput().holdKey(AttackKey.key());
			windup(context);
			aside(context, server);
			context.waitTicks(25);
			context.takeScreenshot("attack-guardian-beam");
			until(context, server, AttackClientTest::targetHurt, 120, "The guardian's beam never hurt the golem");

			// It raises its arms, then the fangs bite under the golem
			arena(context, server, at, "evoker", 6, true);
			camera(context, CameraType.THIRD_PERSON_FRONT);
			context.getInput().holdKey(AttackKey.key());
			windup(context);
			context.waitTicks(5);
			context.takeScreenshot("attack-evoker-casting");
			aside(context, server);
			until(context, server, minecraft -> !owned(minecraft, EvokerFangs.class).isEmpty(), 60, "The evoker cast no fangs of the host's");
			context.waitTicks(8);
			context.takeScreenshot("attack-evoker-fangs");
			until(context, server, AttackClientTest::targetHurt, 60, "The fangs never hurt the golem");

			// It swells and flashes, then blows up beside the golem, sparing the host
			arena(context, server, at, "creeper", 3, false);
			host(server, "effect give @s minecraft:instant_health 1 10 true");
			context.waitTicks(5);
			float health = server.computeOnServer(minecraft -> CommandRunner.host(minecraft).getHealth());
			context.getInput().holdKey(AttackKey.key());
			windup(context);
			// The golem stands where the front camera would
			aside(context, server);
			context.waitTicks(18);
			context.takeScreenshot("attack-creeper-swelling");
			context.waitFor(minecraft -> !minecraft.player.hasAttached(Attacks.WINDUP), 100);
			context.getInput().releaseKey(AttackKey.key());
			context.waitTicks(2);
			context.takeScreenshot("attack-creeper-blast");
			until(context, server, AttackClientTest::targetHurt, 20, "The creeper's blast never hurt the golem");
			float after = server.computeOnServer(minecraft -> CommandRunner.host(minecraft).getHealth());
			if (after < health) throw new AssertionError("The host's own blast hurt them: " + health + " to " + after);
			if (!MorphLooks.failed().isEmpty()) throw new AssertionError("Couldn't draw or animate " + MorphLooks.failed());
		}
	}

	/**
	 * The host as the mob on a clear stone floor, nothing in the camera's way,
	 * facing a still iron golem this far ahead. Up on a pillar they look down at
	 * it, so the camera behind them sees past the mob, and the floor doesn't pull
	 * it into a small mob's tail.
	 */
	private static void arena(ClientGameTestContext context, TestServerContext server, BlockPos at, String mob, int distance, boolean pillar) {
		// The server hears the key go up before the next morph, which keeps it as it is
		context.getInput().releaseKey(AttackKey.key());
		context.waitTicks(5);
		// Only a camera that shows the host draws them as their mob
		camera(context, CameraType.THIRD_PERSON_BACK);
		server.runCommand("kill @e[type=!player]");
		int x = at.getX(), y = at.getY(), z = at.getZ(), up = pillar ? PILLAR : 0;
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x - 16, y - 1, z - 16, x + 16, y - 1, z + 16));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 16, y, z - 16, x + 16, y + 20, z + 16));
		if (pillar) server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x, y, z, x, y + up - 1, z));
		server.runCommand(String.format("tp @p %d.5 %d %d.5 0 0", x, y + up, z));
		server.runCommand(String.format("summon minecraft:iron_golem %d.5 %d %d.5 {NoAI:1b,Tags:[\"%s\"]}", x, y, z + distance, TARGET));
		morph(context, server, mob);
		// From the mob's own eyes
		server.runCommand("execute as @p at @s anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=" + TARGET + ",limit=1] eyes");
		context.waitTicks(5);
	}

	// The windup's attachment reaches this client
	private static void windup(ClientGameTestContext context) {
		context.waitFor(minecraft -> minecraft.player.hasAttached(Attacks.WINDUP), 60);
	}

	private static boolean targetHurt(MinecraftServer minecraft) {
		ServerPlayer host = CommandRunner.host(minecraft);
		return host.level().getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(32), entity -> entity.entityTags().contains(TARGET))
				.stream().anyMatch(target -> !target.isAlive() || target.getHealth() < target.getMaxHealth());
	}

	// What the host's attack made, owned by the host
	private static <T extends Entity> List<T> owned(MinecraftServer minecraft, Class<T> type) {
		ServerPlayer host = CommandRunner.host(minecraft);
		return host.level().getEntitiesOfClass(type, host.getBoundingBox().inflate(64), entity -> entity instanceof TraceableEntity traceable && traceable.getOwner() == host);
	}

	// Checks the server each tick, on the test's thread
	private static void until(ClientGameTestContext context, TestServerContext server, Predicate<MinecraftServer> done, int ticks, String failure) {
		for (int tick = 0; tick < ticks; tick++) {
			if (server.computeOnServer(done::test)) return;
			context.waitTick();
		}
		throw new AssertionError(failure);
	}

	private static void camera(ClientGameTestContext context, CameraType type) {
		context.runOnClient(minecraft -> {
			minecraft.options.setCameraType(type);
			if (!minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
		});
	}

	/**
	 * Turns the host aside, so the camera behind them sees the golem past the
	 * mob: it lines up with their aim. What the attack aimed at stays its
	 * target, and what it shot flies on.
	 */
	private static void aside(ClientGameTestContext context, TestServerContext server) {
		host(server, "tp @s ~ ~ ~ ~35 ~");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(2);
	}

	// Through the command, as Tokio sends it, until this client draws the mob
	private static void morph(ClientGameTestContext context, TestServerContext server, String mob) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(mob));
		CommandResult answer = server.computeOnServer(minecraft -> new CommandRunner(minecraft).run("tokio morph minecraft:" + mob)).join();
		if (!answer.ok()) throw new AssertionError("Tokio got " + answer);
		context.waitFor(minecraft -> Morph.look(minecraft.player) == type, 100);
		context.waitFor(minecraft -> MorphLooks.drawnAs(minecraft.player) == type, 100);
		// Let the camera settle at the new size
		context.waitTicks(10);
	}

	private static void host(TestServerContext server, String command) {
		server.runCommand("execute as @p at @s run " + command);
	}
}
