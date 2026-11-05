package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Spawned;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Tokio's Java command forms, run through {@link CommandRunner} as a mock player who isn't an op. */
public class CommandRunnerTest {
	@GameTest
	public void summonsANamedZombie(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "summon minecraft:zombie ~ ~ ~ {CustomName:\"Lucia\"}");
		Zombie zombie = helper.findOneEntity(EntityTypes.ZOMBIE);
		helper.assertValueEqual(zombie.getCustomName() == null ? null : zombie.getCustomName().getString(), "Lucia", "the zombie's name");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void summonsAtThePlayer(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		CommandResult result = ok(helper, player, "summon minecraft:zombie ~ ~ ~");
		helper.assertValueEqual(result.message(), "Summoned new Zombie", "the feedback");
		Zombie zombie = helper.findOneEntity(EntityTypes.ZOMBIE);
		helper.assertTrue(zombie.position().distanceTo(player.position()) < 0.01, "The zombie isn't at the player");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void summonsAChickenJockey(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "summon minecraft:chicken ~ ~ ~ {Passengers:[{id:\"minecraft:zombie\",IsBaby:1b}]}");
		Zombie rider = jockey(helper, player);
		helper.assertTrue(rider.getCustomName() == null, "The rider has a name");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void summonsANamedChickenJockey(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "summon minecraft:chicken ~ ~ ~ {Passengers:[{id:\"minecraft:zombie\",IsBaby:1b,CustomName:\"Lucia\"}]}");
		Zombie rider = jockey(helper, player);
		helper.assertValueEqual(rider.getCustomName() == null ? null : rider.getCustomName().getString(), "Lucia", "the rider's name");
		player.assertNoChat(helper);
		helper.succeed();
	}

	// A chicken carrying a baby zombie, which is Tokio's and in its grace
	private static Zombie jockey(GameTestHelper helper, MockPlayer player) {
		Chicken chicken = helper.findOneEntity(EntityTypes.CHICKEN);
		helper.assertTrue(chicken.getFirstPassenger() instanceof Zombie, "The chicken carries no zombie");
		Zombie rider = (Zombie) chicken.getFirstPassenger();
		helper.assertTrue(rider.isBaby(), "The rider isn't a baby");
		helper.assertTrue(Spawned.is(rider), "The rider isn't tagged");
		// After the player once its grace is over (SpawnedTest)
		helper.assertTrue(rider.getTarget() == null, "The rider went after the player in its grace");
		return rider;
	}

	@GameTest
	public void givesThePlayerItems(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "give @s minecraft:diamond 3");
		helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 3, "the diamonds");
		player.assertNoChat(helper);
		helper.succeed();
	}

	// Tokio's enchanted items carry the enchantments component, a map of levels
	@GameTest
	public void givesEnchantedItems(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "give @s minecraft:netherite_sword[enchantments={sharpness:5,sweeping_edge:3,fire_aspect:2,knockback:2,looting:3,unbreaking:3,mending:1}] 2");
		helper.assertValueEqual(player.getInventory().countItem(Items.NETHERITE_SWORD), 2, "the swords");
		// Swords don't stack, so the second is in the next slot
		for (int slot = 0; slot < 2; slot++) {
			helper.assertValueEqual(levels(player.getInventory().getItem(slot)), "fire_aspect:2,knockback:2,looting:3,mending:1,sharpness:5,sweeping_edge:3,unbreaking:3", "sword " + slot + "'s enchantments");
		}
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void putsOnEnchantedArmor(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "item replace entity @s armor.feet with minecraft:netherite_boots[enchantments={protection:4,feather_falling:4,soul_speed:3,depth_strider:3,thorns:3,unbreaking:3,mending:1}]");
		ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
		helper.assertTrue(boots.is(Items.NETHERITE_BOOTS), "The player wears no netherite boots");
		helper.assertValueEqual(levels(boots), "depth_strider:3,feather_falling:4,mending:1,protection:4,soul_speed:3,thorns:3,unbreaking:3", "the boots' enchantments");
		player.assertNoChat(helper);
		helper.succeed();
	}

	// Tokio's "replacing yours": the player's own go first, enchanted or not.
	// With none, the clear fails, which Tokio counts as done. The game's own
	// mock player, as /clear updates the crafting grid through a connection.
	@GameTest
	public void replacesThePlayersOwnItems(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			String clear = "clear @s minecraft:netherite_sword";
			String give = "give @s minecraft:netherite_sword[enchantments={sharpness:5,sweeping_edge:3,fire_aspect:2,knockback:2,looting:3,unbreaking:3,mending:1}] 1";
			helper.assertFalse(run(helper, player, clear).ok(), "A clear with nothing to take didn't fail");
			ok(helper, player, give);
			player.getInventory().add(new ItemStack(Items.NETHERITE_SWORD));
			runAll(helper, player, clear, give);
			helper.assertValueEqual(player.getInventory().countItem(Items.NETHERITE_SWORD), 1, "the swords");
			helper.assertValueEqual(levels(player.getInventory().getItem(0)), "fire_aspect:2,knockback:2,looting:3,mending:1,sharpness:5,sweeping_edge:3,unbreaking:3", "the sword's enchantments");
		} finally {
			// Out of the world, where it would be the nearest player of other tests' mobs
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// An item's enchantments as Tokio writes them, sorted by id
	private static String levels(ItemStack stack) {
		return stack.getEnchantments().entrySet().stream()
				.map(entry -> entry.getKey().unwrapKey().orElseThrow().identifier().getPath() + ":" + entry.getIntValue())
				.sorted()
				.collect(Collectors.joining(","));
	}

	@GameTest
	public void changesTheWeather(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "weather thunder");
		helper.assertTrue(helper.getLevel().getServer().getWeatherData().isThundering(), "It isn't thundering");
		ok(helper, player, "weather clear");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void changesTheTime(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
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
		MockPlayer player = new MockPlayer(helper);
		// An action on every gift sets night once per gift, in the same tick
		run(helper, player, "time set day");
		ok(helper, player, "time set night");
		ok(helper, player, "time set night");
		player.assertNoChat(helper);
		helper.succeed();
	}

	// Tokio's launch of 50 blocks clears the 3×3 column over the head first, in
	// two fills, each after a marker that makes sure it changes something
	private static final String[] CLEAR_UP = {
			"setblock ~ ~33 ~ minecraft:structure_void",
			"fill ~-1 ~2 ~-1 ~1 ~33 ~1 minecraft:air",
			"setblock ~ ~52 ~ minecraft:structure_void",
			"fill ~-1 ~34 ~-1 ~1 ~52 ~1 minecraft:air",
	};

	@GameTest(skyAccess = true)
	public void clearsTheWayUpBeforeALaunch(GameTestHelper helper) {
		MockPlayer player = standing(helper, 1);
		// A roof from 3 over the feet up to 40, wider than the column
		fill(helper, 2, 4, 2, 6, 41, 6, Blocks.STONE);
		runAll(helper, player, CLEAR_UP);
		for (int y = 4; y <= 41; y++) {
			for (int x = 3; x <= 5; x++) {
				for (int z = 3; z <= 5; z++) helper.assertBlockNotPresent(Blocks.STONE, x, y, z);
			}
			helper.assertBlockPresent(Blocks.STONE, 2, y, 4);
			helper.assertBlockPresent(Blocks.STONE, 6, y, 4);
		}
		assertNoMarker(helper, 3, 54);
		// Under an open sky, where the fills alone would change nothing
		runAll(helper, player, CLEAR_UP);
		assertNoMarker(helper, 3, 54);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void failsAFillPastTheWorldsTopWithItsMarker(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		player.setPos(new Vec3(player.getX(), helper.getLevel().getMaxY() - 10, player.getZ()));
		for (String commandLine : CLEAR_UP) {
			helper.assertFalse(run(helper, player, commandLine).ok(), "/" + commandLine + " ran past the world's top");
		}
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void trapsThePlayerInCobwebsOnlyWhereThereIsAir(GameTestHelper helper) {
		MockPlayer player = standing(helper, 1);
		helper.setBlock(3, 1, 4, Blocks.STONE);
		String[] trap = {
				"setblock ~ ~1 ~ minecraft:structure_void",
				"setblock ~ ~1 ~ minecraft:air",
				"fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:cobweb keep",
		};
		runAll(helper, player, trap);
		for (int x = 3; x <= 5; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 3; z <= 5; z++) {
					if (x != 3 || y != 1 || z != 4) helper.assertBlockPresent(Blocks.COBWEB, x, y, z);
				}
			}
		}
		helper.assertBlockPresent(Blocks.STONE, 3, 1, 4);
		helper.assertBlockPresent(Blocks.STONE, 4, 0, 4);
		// Again, with webs where the air was
		runAll(helper, player, trap);
		helper.assertBlockPresent(Blocks.COBWEB, 4, 2, 4);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void shutsThePlayerInObsidian(GameTestHelper helper) {
		shell(helper, Blocks.OBSIDIAN, "minecraft:obsidian");
	}

	@GameTest
	public void shutsThePlayerInHoney(GameTestHelper helper) {
		shell(helper, Blocks.HONEY_BLOCK, "minecraft:honey_block");
	}

	// A 5×5×5 shell around the 3×3 space the player stands in, under them too,
	// leaving what's inside
	private static void shell(GameTestHelper helper, Block block, String id) {
		MockPlayer player = standing(helper, 2);
		helper.setBlock(3, 3, 3, Blocks.STONE);
		String[] shell = {"setblock ~ ~3 ~ minecraft:structure_void", "fill ~-2 ~-1 ~-2 ~2 ~3 ~2 " + id + " outline"};
		runAll(helper, player, shell);
		helper.assertBlockPresent(block, 4, 1, 4);
		helper.assertBlockPresent(block, 2, 3, 4);
		helper.assertBlockPresent(block, 6, 2, 3);
		helper.assertBlockPresent(block, 4, 5, 4);
		helper.assertBlockPresent(Blocks.AIR, 4, 2, 4);
		helper.assertBlockPresent(Blocks.AIR, 4, 3, 4);
		helper.assertBlockPresent(Blocks.STONE, 3, 3, 3);
		helper.assertBlockPresent(Blocks.STONE, 4, 0, 4);
		// Again, in the shell already there
		runAll(helper, player, shell);
		helper.assertBlockPresent(block, 4, 5, 4);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void ringsThePlayerWithLavaOnAnIsland(GameTestHelper helper) {
		MockPlayer player = standing(helper, 2);
		String[] lake = {"fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:lava", "setblock ~ ~-1 ~ minecraft:obsidian"};
		runAll(helper, player, lake);
		for (int x = 2; x <= 6; x++) {
			for (int z = 2; z <= 6; z++) {
				helper.assertBlockPresent(x == 4 && z == 4 ? Blocks.OBSIDIAN : Blocks.LAVA, x, 1, z);
				helper.assertBlockPresent(Blocks.STONE, x, 0, z);
			}
		}
		helper.assertBlockPresent(Blocks.STONE, 1, 1, 4);
		// Again, over the lake already there
		runAll(helper, player, lake);
		helper.assertBlockPresent(Blocks.OBSIDIAN, 4, 1, 4);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void digsAHoleAsDeepAsSet(GameTestHelper helper) {
		MockPlayer player = standing(helper, 12);
		String[] hole = hole(10);
		runAll(helper, player, hole);
		for (int y = 2; y <= 11; y++) {
			for (int x = 3; x <= 5; x++) {
				for (int z = 3; z <= 5; z++) helper.assertBlockPresent(Blocks.AIR, x, y, z);
			}
			helper.assertBlockPresent(Blocks.STONE, 2, y, 4);
		}
		helper.assertBlockPresent(Blocks.STONE, 4, 1, 4);
		// Again, over the hole already there
		runAll(helper, player, hole);
		assertNoMarker(helper, 1, 12);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void digsNoHoleOnBedrock(GameTestHelper helper) {
		MockPlayer player = standing(helper, 12);
		// Where the world's floor would be, at the hole's bottom
		helper.setBlock(4, 2, 4, Blocks.BEDROCK);
		for (String commandLine : hole(10)) {
			helper.assertFalse(run(helper, player, commandLine).ok(), "/" + commandLine + " ran on bedrock");
		}
		helper.assertBlockPresent(Blocks.BEDROCK, 4, 2, 4);
		helper.assertBlockPresent(Blocks.STONE, 4, 11, 4);
		player.assertNoChat(helper);
		helper.succeed();
	}

	// Tokio's hole that many blocks deep
	private static String[] hole(int depth) {
		String guard = "execute unless block ~ ~-" + depth + " ~ minecraft:bedrock run ";
		return new String[] {
				guard + "setblock ~ ~-" + depth + " ~ minecraft:structure_void",
				guard + "fill ~-1 ~ ~-1 ~1 ~-" + depth + " ~1 minecraft:air",
		};
	}

	// The game's own mock player, as /clear updates the crafting grid through
	// the player's connection, which MockPlayer has none of
	@GameTest
	public void clearsTheInventory(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.getInventory().add(new ItemStack(Items.DIAMOND, 5));
			player.getInventory().add(new ItemStack(Items.IRON_HELMET));
			String[] clear = {"item replace entity @s hotbar.0 with minecraft:stick", "clear @s"};
			runAll(helper, player, clear);
			helper.assertTrue(player.getInventory().isEmpty(), "The inventory isn't empty");
			// Again, on the empty inventory
			runAll(helper, player, clear);
			helper.assertTrue(player.getInventory().isEmpty(), "The inventory isn't empty");
		} finally {
			// Out of the world, where it would be the nearest player of other tests' mobs
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// The mock player in the middle of a stone floor this many blocks thick
	private static MockPlayer standing(GameTestHelper helper, int depth) {
		fill(helper, 0, 0, 0, 7, depth - 1, 7, Blocks.STONE);
		MockPlayer player = new MockPlayer(helper);
		player.setPos(helper.absoluteVec(new Vec3(4.5, depth, 4.5)));
		return player;
	}

	private static void fill(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) helper.setBlock(x, y, z, block);
			}
		}
	}

	// No structure void left in the column over the test's middle
	private static void assertNoMarker(GameTestHelper helper, int from, int to) {
		for (int y = from; y <= to; y++) helper.assertBlockNotPresent(Blocks.STRUCTURE_VOID, 4, y, 4);
	}

	@GameTest
	public void failsAnUnknownMob(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		CommandResult result = run(helper, player, "summon minecraft:not_a_mob ~ ~ ~");
		helper.assertFalse(result.ok(), "A summon of an unknown mob worked");
		helper.assertTrue(result.message().contains("minecraft:not_a_mob"), "Unexpected error: " + result.message());
		helper.assertEntityNotPresent(EntityTypes.ZOMBIE);
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void failsACommandThatCantRun(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		CommandResult summon = run(helper, player, "summon minecraft:zombie 40000000 ~ 0");
		helper.assertValueEqual(summon, new CommandResult(false, "Invalid position for summon"), "the summon");
		CommandResult give = run(helper, player, "give @s minecraft:diamond 99999");
		helper.assertValueEqual(give, new CommandResult(false, "Can't give more than 6400 of [Diamond]"), "the give");
		player.assertNoChat(helper);
		helper.succeed();
	}

	@GameTest
	public void failsGibberish(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
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

	private static CommandResult ok(GameTestHelper helper, ServerPlayer player, String commandLine) {
		CommandResult result = run(helper, player, commandLine);
		helper.assertTrue(result.ok(), "/" + commandLine + " failed: " + result.message());
		return result;
	}

	private static void runAll(GameTestHelper helper, ServerPlayer player, String... commandLines) {
		for (String commandLine : commandLines) ok(helper, player, commandLine);
	}

	private static CommandResult run(GameTestHelper helper, ServerPlayer player, String commandLine) {
		// The test runs on the server thread, so the command runs at once
		return new CommandRunner(helper.getLevel().getServer(), () -> player).run(commandLine).getNow(null);
	}
}
