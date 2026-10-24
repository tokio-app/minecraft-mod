package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Tokio's Java command forms, run through {@link CommandRunner} as a mock player who isn't an op. */
public class CommandRunnerTest {
	@GameTest
	public void summonsANamedZombie(GameTestHelper helper) {
		Player player = new Player(helper);
		ok(helper, player, "summon minecraft:zombie ~ ~ ~ {CustomName:\"Lucia\"}");
		Zombie zombie = helper.findOneEntity(EntityTypes.ZOMBIE);
		helper.assertValueEqual(zombie.getCustomName() == null ? null : zombie.getCustomName().getString(), "Lucia", "the zombie's name");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void summonsAtThePlayer(GameTestHelper helper) {
		Player player = new Player(helper);
		CommandResult result = ok(helper, player, "summon minecraft:zombie ~ ~ ~");
		helper.assertValueEqual(result.message(), "Summoned new Zombie", "the feedback");
		Zombie zombie = helper.findOneEntity(EntityTypes.ZOMBIE);
		helper.assertTrue(zombie.position().distanceTo(player.position()) < 0.01, "The zombie isn't at the player");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void givesThePlayerItems(GameTestHelper helper) {
		Player player = new Player(helper);
		ok(helper, player, "give @s minecraft:diamond 3");
		helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 3, "the diamonds");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void changesTheWeather(GameTestHelper helper) {
		Player player = new Player(helper);
		ok(helper, player, "weather thunder");
		helper.assertTrue(helper.getLevel().getServer().getWeatherData().isThundering(), "It isn't thundering");
		ok(helper, player, "weather clear");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void changesTheTime(GameTestHelper helper) {
		Player player = new Player(helper);
		// Fails when it's already day; it only makes sure night is a change
		run(helper, player, "time set day");
		ok(helper, player, "time set night");
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.getServer().clockManager().isAtTimeMarker(level.dimensionType().defaultClock().orElseThrow(), ClockTimeMarkers.NIGHT), "It isn't night");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void setsTheTimeItIsAlready(GameTestHelper helper) {
		Player player = new Player(helper);
		// An action on every gift sets night once per gift, in the same tick
		run(helper, player, "time set day");
		ok(helper, player, "time set night");
		ok(helper, player, "time set night");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void failsAnUnknownMob(GameTestHelper helper) {
		Player player = new Player(helper);
		CommandResult result = run(helper, player, "summon minecraft:not_a_mob ~ ~ ~");
		helper.assertFalse(result.ok(), "A summon of an unknown mob worked");
		helper.assertTrue(result.message().contains("minecraft:not_a_mob"), "Unexpected error: " + result.message());
		helper.assertEntityNotPresent(EntityTypes.ZOMBIE);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void failsACommandThatCantRun(GameTestHelper helper) {
		Player player = new Player(helper);
		CommandResult summon = run(helper, player, "summon minecraft:zombie 40000000 ~ 0");
		helper.assertValueEqual(summon, new CommandResult(false, "Invalid position for summon"), "the summon");
		CommandResult give = run(helper, player, "give @s minecraft:diamond 99999");
		helper.assertValueEqual(give, new CommandResult(false, "Can't give more than 6400 of [Diamond]"), "the give");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void failsGibberish(GameTestHelper helper) {
		Player player = new Player(helper);
		CommandResult result = run(helper, player, "definitely not a command");
		helper.assertFalse(result.ok(), "Gibberish worked");
		helper.assertTrue(result.message().startsWith("Unknown or incomplete command"), "Unexpected error: " + result.message());
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void failsWithoutAPlayer(GameTestHelper helper) {
		CommandResult result = new CommandRunner(helper.getLevel().getServer(), () -> null).run("summon minecraft:zombie ~ ~ ~").join();
		helper.assertValueEqual(result, new CommandResult(false, CommandRunner.NO_PLAYER), "the result");
		helper.assertEntityNotPresent(EntityTypes.ZOMBIE);
		helper.succeed();
	}

	private static CommandResult ok(GameTestHelper helper, Player player, String commandLine) {
		CommandResult result = run(helper, player, commandLine);
		helper.assertTrue(result.ok(), "/" + commandLine + " failed: " + result.message());
		return result;
	}

	private static CommandResult run(GameTestHelper helper, Player player, String commandLine) {
		// The test runs on the server thread, so the command runs at once
		return new CommandRunner(helper.getLevel().getServer(), () -> player).run(commandLine).getNow(null);
	}

	/** A survival player in the test's area, who isn't an op and keeps the chat lines they get. */
	private static final class Player extends ServerPlayer {
		private final List<String> chat = new ArrayList<>();

		Player(GameTestHelper helper) {
			super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player"), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
			setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
			helper.assertFalse(permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "The mock player has gamemaster permission");
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}

		@Override
		public void sendSystemMessage(Component message, boolean overlay) {
			chat.add(message.getString());
		}

		void assertNoChat(GameTestHelper helper) {
			helper.assertTrue(chat.isEmpty(), "The player got chat lines: " + chat);
		}
	}
}
