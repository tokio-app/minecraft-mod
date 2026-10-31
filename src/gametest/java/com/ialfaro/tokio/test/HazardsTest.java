package com.ialfaro.tokio.test;

import com.ialfaro.tokio.game.Laser;
import com.ialfaro.tokio.game.Meteor;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The meteor and the laser, aimed at a mock player. Game tests have no world
 * host, who alone has /tokio, so they start the hazards themselves. The
 * padding keeps the blasts away from the other tests.
 */
public class HazardsTest {
	@GameTest(padding = 16, maxTicks = 200)
	public void meteorLeavesARock(GameTestHelper helper) {
		// Obsidian holds, so the rock lands where the player stands
		MockPlayer player = onAFloor(helper, Blocks.OBSIDIAN);
		BlockPos landing = BlockPos.containing(player.position());
		List<Meteor> meteors = Meteor.fall(player, 1);
		helper.assertFalse(meteors.getFirst().shouldBeSaved(), "A meteor would be saved with the world");
		helper.succeedWhen(() -> {
			helper.assertTrue(meteors.getFirst().isRemoved(), "The meteor is still falling");
			helper.assertTrue(helper.getLevel().getBlockState(landing).is(Blocks.MAGMA_BLOCK), "No magma where the meteor landed");
		});
	}

	@GameTest(padding = 16, maxTicks = 200)
	public void laserBlastsAHole(GameTestHelper helper) {
		MockPlayer player = onAFloor(helper, Blocks.STONE);
		BlockPos under = BlockPos.containing(player.position()).below();
		Laser laser = Laser.fire(player);
		helper.assertFalse(laser.shouldBeSaved(), "A laser would be saved with the world");
		helper.succeedWhen(() -> {
			helper.assertTrue(laser.isRemoved(), "The laser is still firing");
			// Fire may take the hole's place
			helper.assertFalse(helper.getLevel().getBlockState(under).is(Blocks.STONE), "The floor under the player is still there");
		});
	}

	// The mock player stands on a floor of the block, and the chunks a meteor
	// starts in are loaded: only the test's own are
	private static MockPlayer onAFloor(GameTestHelper helper, Block floor) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) helper.setBlock(x, 0, z, floor);
		}
		MockPlayer player = new MockPlayer(helper);
		ChunkPos chunk = player.chunkPosition();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) helper.getLevel().setChunkForced(chunk.x() + x, chunk.z() + z, true);
		}
		return player;
	}
}
