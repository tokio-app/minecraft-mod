package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Spawned;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What the mobs Tokio's commands spawn do: they never burn in daylight, go
 * after no one in their grace, and then the hostile ones go after the player.
 */
public class SpawnedTest {
	// The tests that wait out the grace need more than the 20 ticks a test gets
	@GameTest(maxTicks = 100)
	public void zombiesGoAfterThePlayerAfterTheirGrace(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "summon minecraft:zombie ~ ~ ~");
		Zombie zombie = helper.findOneEntity(EntityTypes.ZOMBIE);
		helper.assertTrue(Spawned.is(zombie), "The zombie isn't tagged");
		afterTheirGrace(helper, player, List.of(zombie), () -> {
		});
	}

	@GameTest(maxTicks = 100)
	public void neutralMonstersAreAngryAtThePlayer(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "summon minecraft:enderman ~ ~ ~");
		ok(helper, player, "summon minecraft:zombified_piglin ~ ~ ~");
		List<Mob> mobs = List.of(helper.findOneEntity(EntityTypes.ENDERMAN), helper.findOneEntity(EntityTypes.ZOMBIFIED_PIGLIN));
		afterTheirGrace(helper, player, mobs, () -> {
			for (Mob mob : mobs) {
				helper.assertTrue(((NeutralMob) mob).isAngryAt(player, helper.getLevel()), "The " + mob.getType() + " isn't angry at the player");
			}
		});
	}

	@GameTest(maxTicks = 100)
	public void brainMobsGoAfterThePlayer(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		for (String id : new String[] {"piglin", "piglin_brute", "hoglin", "zoglin", "warden", "breeze", "creaking"}) {
			ok(helper, player, "summon minecraft:" + id + " ~ ~ ~");
		}
		List<Mob> mobs = new ArrayList<>();
		for (EntityType<? extends Mob> type : List.of(EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE, EntityTypes.HOGLIN, EntityTypes.ZOGLIN, EntityTypes.WARDEN, EntityTypes.BREEZE, EntityTypes.CREAKING)) {
			mobs.add(helper.findOneEntity(type));
		}
		afterTheirGrace(helper, player, mobs, () -> {
		});
	}

	// Touching needs no target, so a cube's grace has its own guard. The player
	// is in the world, where they can be hurt, for this tick alone.
	@GameTest
	public void cubesHurtByTouchOnlyAfterTheirGrace(GameTestHelper helper) {
		ServerPlayer player = HostOnlyTest.inTheWorld(helper, new Vec3(2.5, 1, 2.5));
		try {
			ok(helper, player, "summon minecraft:magma_cube ~ ~ ~");
			MagmaCube cube = helper.findOneEntity(EntityTypes.MAGMA_CUBE);
			cube.playerTouch(player);
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "The magma cube hurt the player in its grace");
			cube.tickCount = Spawned.GRACE_TICKS;
			cube.playerTouch(player);
			helper.assertTrue(player.getHealth() < player.getMaxHealth(), "The magma cube didn't hurt the player after its grace");
		} finally {
			// Out of the world, where it would be the nearest player of other tests' mobs
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// Each tick of their grace, the mobs go after no one, even when told to; then
	// they're after the player, and `after` holds
	private static void afterTheirGrace(GameTestHelper helper, MockPlayer player, List<? extends Mob> mobs, Runnable after) {
		helper.startSequence()
				.thenExecuteFor(Spawned.GRACE_TICKS - 5, () -> {
					for (Mob mob : mobs) {
						mob.setTarget(player);
						helper.assertTrue(mob.getTarget() == null, "The " + mob.getType() + " went after the player in its grace");
					}
				})
				.thenWaitUntil(() -> {
					for (Mob mob : mobs) helper.assertTrue(mob.getTarget() == player, "The " + mob.getType() + " isn't after the player");
					after.run();
				})
				.thenSucceed();
	}

	@GameTest
	public void cowsAreLeftAlone(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "summon minecraft:cow ~ ~ ~");
		Mob cow = helper.findOneEntity(EntityTypes.COW);
		helper.assertTrue(Spawned.is(cow), "The cow isn't tagged");
		helper.assertTrue(cow.getTarget() == null, "The cow is after the player");
		helper.succeed();
	}

	@GameTest
	public void naturalSpawnsArentTokios(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
		ok(helper, player, "give @s minecraft:diamond");
		helper.assertFalse(Spawned.is(zombie), "A zombie Tokio didn't spawn is tagged");
		helper.assertTrue(zombie.getTarget() == null, "A zombie Tokio didn't spawn is after the player");
		helper.succeed();
	}

	// In the morph sun test's batch at noon: the super lasers in tokio-test:noon bring the night
	@GameTest(environment = "tokio-test:noon_morph", maxTicks = 400, skyAccess = true)
	public void zombiesDontBurnInTheSun(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "weather clear");
		ok(helper, player, "summon minecraft:zombie ~ ~ ~");
		Zombie tokios = helper.findOneEntity(EntityTypes.ZOMBIE);
		// The control, spawned as the world would; a helmet would keep the sun off either
		Zombie control = helper.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
		tokios.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		control.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		helper.onEachTick(() -> helper.assertFalse(tokios.isOnFire(), "Tokio's zombie caught fire"));
		// About 4% a tick in full sun: 200 ticks leave Tokio's zombie a 1 in 3,000 chance to have dodged it by luck
		helper.succeedWhen(() -> {
			helper.assertTrue(control.isOnFire(), "The control zombie isn't burning");
			helper.assertTrue(helper.getTick() >= 200, "Not 200 ticks yet");
		});
	}

	// The game has a spider in daylight give up about 1 tick in 100
	@GameTest(environment = "tokio-test:noon_morph", maxTicks = 400, skyAccess = true)
	public void spidersKeepAttackingInTheSun(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		ok(helper, player, "weather clear");
		ok(helper, player, "summon minecraft:spider ~ ~ ~");
		Mob spider = helper.findOneEntity(EntityTypes.SPIDER);
		// A dead player is no target
		helper.onEachTick(() -> player.setHealth(player.getMaxHealth()));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(spider.getTarget() == player, "The spider gave up");
			helper.succeed();
		});
	}

	static CommandResult ok(GameTestHelper helper, ServerPlayer player, String commandLine) {
		// The test runs on the server thread, so the command runs at once
		CommandResult result = new CommandRunner(helper.getLevel().getServer(), () -> player).run(commandLine).getNow(null);
		helper.assertTrue(result.ok(), "/" + commandLine + " failed: " + result.message());
		return result;
	}
}
