package com.ialfaro.tokio.test;

import com.ialfaro.tokio.game.Laser;
import com.ialfaro.tokio.game.Meteor;
import com.ialfaro.tokio.game.Night;
import java.util.List;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The meteor and the lasers, aimed at a mock player. Game tests have no world
 * host, who alone has /tokio, so they start the hazards themselves. The
 * padding keeps the blasts away from the other tests.
 */
public class HazardsTest {
	@GameTest(padding = 16, maxTicks = 200)
	public void meteorLeavesARock(GameTestHelper helper) {
		// Obsidian holds, so the rock lands where the player stands
		MockPlayer player = onAFloor(helper, Blocks.OBSIDIAN, 1);
		BlockPos landing = BlockPos.containing(player.position());
		List<Meteor> meteors = Meteor.fall(player, 1);
		helper.assertFalse(meteors.getFirst().shouldBeSaved(), "A meteor would be saved with the world");
		helper.succeedWhen(() -> {
			helper.assertTrue(meteors.getFirst().isRemoved(), "The meteor is still falling");
			helper.assertTrue(helper.getLevel().getBlockState(landing).is(Blocks.MAGMA_BLOCK), "No magma where the meteor landed");
		});
	}

	// Without a barrier ceiling over the test, which the player would be above
	@GameTest(padding = 16, maxTicks = 200, skyAccess = true)
	public void meteorLandsUnderAPlayerInTheAir(GameTestHelper helper) {
		MockPlayer player = onAFloor(helper, Blocks.OBSIDIAN, 1);
		BlockPos landing = BlockPos.containing(player.position());
		// Higher than a rock falls from a blast in the air
		player.setPos(player.position().add(0, 10, 0));
		Meteor.fall(player, 1);
		helper.succeedWhen(() -> helper.assertTrue(helper.getLevel().getBlockState(landing).is(Blocks.MAGMA_BLOCK), "No magma on the ground under the player"));
	}

	@GameTest(padding = 16, maxTicks = 200)
	public void laserBlastsAHole(GameTestHelper helper) {
		MockPlayer player = onAFloor(helper, Blocks.STONE, 1);
		BlockPos under = BlockPos.containing(player.position()).below();
		Laser laser = Laser.fire(player, Laser.LASER);
		helper.assertFalse(laser.shouldBeSaved(), "A laser would be saved with the world");
		helper.succeedWhen(() -> {
			helper.assertTrue(laser.isRemoved(), "The laser is still firing");
			// Fire may take the hole's place
			helper.assertFalse(helper.getLevel().getBlockState(under).is(Blocks.STONE), "The floor under the player is still there");
		});
	}

	// In its own batch, which starts at noon: the other tests set the time too
	@GameTest(environment = "tokio-test:noon", padding = 32, maxTicks = 300)
	public void superLaserBoresDeepAtNight(GameTestHelper helper) {
		MockPlayer player = onAFloor(helper, Blocks.STONE, 6);
		BlockPos spot = BlockPos.containing(player.position());
		// Stone on under the floor, 22 deep in all and wide enough for the
		// tunnel's lean, so a gap 12 down is the bore's: blasts barely dent stone
		BlockPos.betweenClosed(spot.offset(-6, -22, -6), spot.offset(6, -7, 6)).forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
		ServerLevel level = helper.getLevel();
		Holder<WorldClock> clock = level.dimensionType().defaultClock().orElseThrow();
		LongSupplier hour = () -> level.getServer().clockManager().getInstance(clock).totalTicks() % 24000;
		// The mod is client-only, so the test server never runs Tokio's
		// initializer, which ticks the night
		helper.onEachTick(() -> Night.tick(level.getServer()));
		Laser laser = Laser.fire(player, Laser.SUPER);
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(hour.getAsLong() >= 13000 && hour.getAsLong() < 23000, "It isn't night while the super laser charges: " + hour.getAsLong());
			helper.assertTrue(lit(level, spot), "The super laser doesn't light the spot up");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(laser.isRemoved(), "The super laser is still firing");
			helper.assertFalse(lit(level, spot), "The super laser's light is still there");
			boolean deep = BlockPos.betweenClosedStream(spot.offset(-6, -12, -6), spot.offset(6, -12, 6)).anyMatch(pos -> level.getBlockState(pos).isAir());
			helper.assertTrue(deep, "The super laser didn't bore 12 blocks down");
			// The hour the batch's first night fell at: about the noon it set
			long now = hour.getAsLong();
			helper.assertTrue(now >= 5000 && now < 7000, "The day isn't back: " + now);
		});
	}

	// Alone in its batch, as a super laser in another test would hold the night
	@GameTest(environment = "tokio-test:noon_alone", maxTicks = 200)
	public void nightLiftsOnceNoLaserHoldsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<WorldClock> clock = level.dimensionType().defaultClock().orElseThrow();
		LongSupplier hour = () -> level.getServer().clockManager().getInstance(clock).totalTicks() % 24000;
		helper.startSequence()
			// A super laser holding it, every tick
			.thenExecuteFor(40, () -> {
				Night.hold(level);
				Night.tick(level.getServer());
			})
			.thenExecute(() -> helper.assertTrue(hour.getAsLong() >= 13000 && hour.getAsLong() < 23000, "Night didn't fall: " + hour.getAsLong()))
			// Then none, as when it's left behind and stops ticking without ending
			.thenExecuteFor(40, () -> Night.tick(level.getServer()))
			.thenExecute(() -> helper.assertTrue(hour.getAsLong() >= 5000 && hour.getAsLong() < 7000, "The day isn't back: " + hour.getAsLong()))
			.thenSucceed();
	}

	// Beside the super laser test, as it brings the night too; both ticking it
	// only makes the sky run faster
	@GameTest(environment = "tokio-test:noon", padding = 32, maxTicks = 300)
	public void superDuperLaserBurnsThroughTheGroundAbove(GameTestHelper helper) {
		MockPlayer player = onAFloor(helper, Blocks.STONE, 1);
		BlockPos spot = BlockPos.containing(player.position());
		// A cave: stone from 2 over the player's feet up to 14, which blasts at
		// the spot can't get through
		BlockPos.betweenClosed(spot.offset(-6, 2, -6), spot.offset(6, 14, 6)).forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
		helper.onEachTick(() -> Night.tick(helper.getLevel().getServer()));
		Laser laser = Laser.fire(player, Laser.SUPER_DUPER);
		helper.succeedWhen(() -> {
			helper.assertTrue(laser.isRemoved(), "The super duper laser is still firing");
			helper.assertFalse(helper.getLevel().getBlockState(spot.above(4)).is(Blocks.STONE), "The ground over the player is still there");
		});
	}

	// The mock player stands in the middle of a floor this many blocks thick,
	// and the chunks a meteor starts in are loaded: only the test's own are
	private static MockPlayer onAFloor(GameTestHelper helper, Block floor, int depth) {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < depth; y++) {
				for (int z = 0; z < 8; z++) helper.setBlock(x, y, z, floor);
			}
		}
		MockPlayer player = new MockPlayer(helper);
		player.setPos(helper.absoluteVec(new Vec3(4.5, depth, 4.5)));
		ChunkPos chunk = player.chunkPosition();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) helper.getLevel().setChunkForced(chunk.x() + x, chunk.z() + z, true);
		}
		return player;
	}

	// A light block anywhere the super laser's light could be
	private static boolean lit(ServerLevel level, BlockPos spot) {
		return BlockPos.betweenClosedStream(spot.offset(-7, 0, -7), spot.offset(7, 18, 7)).anyMatch(pos -> level.getBlockState(pos).is(Blocks.LIGHT));
	}
}
