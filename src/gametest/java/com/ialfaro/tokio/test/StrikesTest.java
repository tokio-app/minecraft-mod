package com.ialfaro.tokio.test;

import com.ialfaro.tokio.game.Attack;
import com.ialfaro.tokio.game.Attacks;
import com.ialfaro.tokio.game.Hits;
import com.ialfaro.tokio.game.Morph;
import com.ialfaro.tokio.game.Spawned;
import java.util.List;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The morphs' attacks that hit without a projectile ({@code game/Strikes}),
 * and their melee hits ({@code game/Hits}), on a mock player at (1.5, 1, 1.5)
 * looking along x. The tests tick {@link Morph#tick}, which runs the attacks;
 * the hits are called as Fabric's {@code AFTER_DAMAGE} would.
 */
public class StrikesTest {
	@GameTest
	public void theirPaceIsTheMobs(GameTestHelper helper) {
		pace(helper, EntityTypes.WARDEN, 34, 66);
		pace(helper, EntityTypes.EVOKER, 20, 80);
		pace(helper, EntityTypes.GUARDIAN, 80, 20);
		pace(helper, EntityTypes.ELDER_GUARDIAN, 60, 20);
		pace(helper, EntityTypes.CREEPER, 30, 30);
		helper.succeed();
	}

	@GameTest(padding = 16, maxTicks = 50)
	public void wardensBoomHitsThroughAWallAfterItsCharge(GameTestHelper helper) {
		for (int y = 1; y < 5; y++) {
			for (int z = 0; z < 4; z++) helper.setBlock(6, y, z, Blocks.STONE);
		}
		IronGolem golem = target(helper, 11);
		MockPlayer player = attacker(helper, EntityTypes.WARDEN);
		float health = golem.getMaxHealth();
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(golem.getHealth() == health, "The boom hit before its charge");
			helper.assertTrue(player.getAttached(Attacks.WINDUP) != null, "The Warden isn't charging");
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(golem.getHealth() == health - 10, "The boom didn't hit through the wall for 10: " + golem.getHealth());
			end(helper, player);
		});
	}

	// Its line runs 20 blocks out, past the test's own area
	@GameTest(padding = 24, maxTicks = 40)
	public void evokersFangsLineUpAfterItsWarmup(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) helper.setBlock(x, 0, 1, Blocks.STONE);
		MockPlayer player = attacker(helper, EntityTypes.EVOKER);
		helper.runAfterDelay(15, () -> helper.assertTrue(fangs(helper, player).isEmpty(), "Fangs came before the warmup"));
		helper.runAfterDelay(25, () -> {
			List<EvokerFangs> fangs = fangs(helper, player);
			helper.assertValueEqual(fangs.size(), 16, "the fangs");
			for (EvokerFangs fang : fangs) {
				helper.assertTrue(Spawned.is(fang), "A fang isn't Tokio's");
				helper.assertTrue(Math.abs(fang.getZ() - player.getZ()) < 1.0E-4, "A fang is off the player's look");
				helper.assertTrue(fang.getY() == helper.absoluteVec(new Vec3(0, 1, 0)).y, "A fang isn't on the ground: " + fang.position());
			}
			end(helper, player);
		});
	}

	@GameTest(padding = 16, maxTicks = 50)
	public void wardensBoomGoesAfterWhatItChargedAt(GameTestHelper helper) {
		IronGolem golem = target(helper, 4);
		MockPlayer player = attacker(helper, EntityTypes.WARDEN);
		float health = golem.getMaxHealth();
		// It looks the other way as it charges
		helper.runAfterDelay(5, () -> {
			player.setYRot(90);
			player.setYHeadRot(90);
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(golem.getHealth() == health - 10, "The boom didn't go after what it charged at: " + golem.getHealth());
			end(helper, player);
		});
	}

	@GameTest(maxTicks = 40)
	public void evokersFangsRingAroundWhatsClose(GameTestHelper helper) {
		for (int x = 1; x < 8; x++) {
			for (int z = 1; z < 8; z++) helper.setBlock(x, 0, z, Blocks.STONE);
		}
		MockPlayer player = attacker(helper, EntityTypes.EVOKER);
		// In the middle, with room for the rings, 2 blocks from the golem
		player.setPos(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
		helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, 6.5F, 1, 4.5F);
		helper.runAfterDelay(25, () -> {
			// 5 at 1.5 blocks and 8 at 2.5
			helper.assertValueEqual(fangs(helper, player).size(), 13, "the fangs around the player");
			end(helper, player);
		});
	}

	@GameTest(maxTicks = 100)
	public void guardiansBeamHurtsAfterItsCharge(GameTestHelper helper) {
		IronGolem golem = target(helper, 5);
		MockPlayer player = attacker(helper, EntityTypes.GUARDIAN);
		float health = golem.getMaxHealth();
		helper.runAfterDelay(5, () -> helper.assertValueEqual(player.getAttached(Attacks.WINDUP), golem.getId(), "the beam's target"));
		helper.runAfterDelay(75, () -> helper.assertTrue(golem.getHealth() == health, "The beam hurt before its charge"));
		helper.runAfterDelay(90, () -> {
			// Its magic, then its hit for the guardian's 6
			helper.assertTrue(golem.getHealth() == health - 6, "The beam didn't hurt for 6: " + (health - golem.getHealth()));
			end(helper, player);
		});
	}

	@GameTest
	public void guardiansBeamNeedsATargetInSight(GameTestHelper helper) {
		MockPlayer player = attacker(helper, EntityTypes.GUARDIAN);
		helper.runAfterDelay(3, () -> helper.assertTrue(player.getAttached(Attacks.WINDUP) == null, "The beam started without a target"));
		IronGolem[] golem = new IronGolem[1];
		helper.runAfterDelay(4, () -> golem[0] = target(helper, 5));
		helper.runAfterDelay(8, () -> {
			helper.assertValueEqual(player.getAttached(Attacks.WINDUP), golem[0].getId(), "the beam's target");
			for (int y = 1; y < 5; y++) {
				for (int z = 0; z < 4; z++) helper.setBlock(3, y, z, Blocks.STONE);
			}
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(player.getAttached(Attacks.WINDUP) == null, "The beam goes on behind a wall");
			helper.assertTrue(golem[0].getHealth() == golem[0].getMaxHealth(), "The beam hurt what hid");
			end(helper, player);
		});
	}

	@GameTest(padding = 16, maxTicks = 110)
	public void creepersBlastComesAfterItsFuseAndAgainAfterItsCooldown(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 4; z++) helper.setBlock(x, 0, z, Blocks.OBSIDIAN);
		}
		IronGolem golem = target(helper, 3);
		Vec3 at = golem.position();
		MockPlayer player = attacker(helper, EntityTypes.CREEPER);
		float[] health = {golem.getMaxHealth()};
		helper.runAfterDelay(25, () -> helper.assertTrue(golem.getHealth() == health[0], "The blast came before the fuse"));
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(golem.getHealth() < health[0], "The blast didn't hurt the mob beside the player");
			health[0] = golem.getHealth();
			// Back beside the player for the next one
			golem.teleportTo(at.x, at.y, at.z);
			golem.setDeltaMovement(Vec3.ZERO);
		});
		// The next fuse starts 30 ticks after the blast
		helper.runAfterDelay(85, () -> helper.assertTrue(golem.getHealth() == health[0], "The blast came again before its cooldown and fuse"));
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(golem.getHealth() < health[0], "The blast didn't come again after its cooldown");
			end(helper, player);
		});
	}

	@GameTest
	public void hitsAddWhatTheMobsOwnAdd(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Difficulty difficulty = helper.getLevel().getDifficulty();
		float regional = helper.getLevel().getCurrentDifficultyAt(player.blockPosition()).getEffectiveDifficulty();
		effect(helper, hit(helper, player, EntityTypes.WITHER_SKELETON), MobEffects.WITHER, 200, "a wither skeleton's");
		effect(helper, hit(helper, player, EntityTypes.HUSK), MobEffects.HUNGER, 140 * (int) regional, "a husk's");
		effect(helper, hit(helper, player, EntityTypes.CAVE_SPIDER), MobEffects.POISON, byDifficulty(difficulty, 7, 15), "a cave spider's");
		Pig stung = hit(helper, player, EntityTypes.BEE);
		effect(helper, stung, MobEffects.POISON, byDifficulty(difficulty, 10, 18), "a bee's");
		helper.assertValueEqual(stung.getStingerCount(), 1, "the stingers in what a bee stung");
		Pig tossed = hit(helper, player, EntityTypes.IRON_GOLEM);
		helper.assertTrue(Math.abs(tossed.getDeltaMovement().y - 0.4) < 1.0E-4, "An iron golem's punch didn't toss up: " + tossed.getDeltaMovement());
		for (EntityType<?> type : List.of(EntityTypes.HOGLIN, EntityTypes.ZOGLIN)) {
			Vec3 thrown = hit(helper, player, type).getDeltaMovement();
			helper.assertTrue(thrown.horizontalDistanceSqr() > 0, type + "'s hit didn't throw");
		}
		// Only a melee hit that landed, and never on a player
		Morph.start(player, EntityTypes.WITHER_SKELETON, OptionalInt.empty());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, 2.5F, 1, 1.5F);
		Hits.afterDamage(pig, player.damageSources().mobAttack(player), 1, 1, false);
		Hits.afterDamage(pig, player.damageSources().playerAttack(player), 1, 1, true);
		Hits.afterDamage(pig, player.damageSources().playerAttack(player), 1, 0, false);
		helper.assertTrue(pig.getActiveEffects().isEmpty(), "What wasn't a landed melee hit added an effect");
		MockPlayer other = new MockPlayer(helper);
		Hits.afterDamage(other, player.damageSources().playerAttack(player), 1, 1, false);
		helper.assertTrue(other.getActiveEffects().isEmpty(), "A hit added an effect to a player");
		Morph.end(player);
		Hits.afterDamage(pig, player.damageSources().playerAttack(player), 1, 1, false);
		helper.assertTrue(pig.getActiveEffects().isEmpty(), "A player who isn't a mob added an effect");
		helper.succeed();
	}

	// The host and another player in the world, for one tick, as HostOnlyTest makes them: the
	// host's boom, fangs, beam, blast and hits pass the other player by
	@GameTest(padding = 16)
	public void strikesAndHitsSpareOtherPlayers(GameTestHelper helper) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 7; z++) helper.setBlock(x, 0, z, Blocks.OBSIDIAN);
		}
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer host = HostOnlyTest.inTheWorld(helper, new Vec3(1.5, 1, 3.5));
		ServerPlayer guest = HostOnlyTest.inTheWorld(helper, new Vec3(3.5, 1, 3.5));
		host.setYRot(-90);
		host.setYHeadRot(-90);
		host.setXRot(0);
		server.setSingleplayerProfile(host.getGameProfile());
		try {
			// The other player stands between the host and the mob they look at
			IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, 7.5F, 1, 3.5F);
			Attacks.of(EntityTypes.WARDEN).fire(host, null);
			helper.assertTrue(golem.getHealth() < golem.getMaxHealth(), "The boom didn't reach the mob past the other player");
			spared(helper, host, guest, "The boom");

			guest.setPos(host.position().add(1.25, 0, 0));
			float before = golem.getHealth();
			// The boom's hit spares it from smaller ones for a second, and it doesn't tick here
			golem.damageCooldownTime = 0;
			Attacks.of(EntityTypes.EVOKER).fire(host, null);
			List<EvokerFangs> fangs = fangs(helper, host);
			for (int tick = 0; tick < 40; tick++) fangs.forEach(EvokerFangs::tick);
			fangs.forEach(EvokerFangs::discard);
			helper.assertTrue(golem.getHealth() < before, "The fangs didn't bite the mob");
			spared(helper, host, guest, "The fangs");

			golem.discard();
			helper.assertTrue(Attacks.of(EntityTypes.GUARDIAN).start(host) == null, "The beam aimed at another player");

			IronGolem beside = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, 1.5F, 1, 5.5F);
			Attacks.of(EntityTypes.CREEPER).fire(host, null);
			helper.assertTrue(beside.getHealth() < beside.getMaxHealth(), "The blast didn't hurt the mob");
			spared(helper, host, guest, "The blast");

			Morph.start(host, EntityTypes.WITHER_SKELETON, OptionalInt.empty());
			DamageSource punch = host.damageSources().playerAttack(host);
			Hits.afterDamage(guest, punch, 1, 1, false);
			Hits.afterDamage(beside, punch, 1, 1, false);
			Morph.end(host);
			helper.assertTrue(beside.hasEffect(MobEffects.WITHER), "The hit didn't wither the mob");
			helper.assertFalse(guest.hasEffect(MobEffects.WITHER), "The hit withered another player");
		} finally {
			server.setSingleplayerProfile(null);
			server.getPlayerList().remove(host);
			server.getPlayerList().remove(guest);
		}
		helper.succeed();
	}

	private static void pace(GameTestHelper helper, EntityType<?> type, int windup, int cooldown) {
		Attack attack = Attacks.of(type);
		helper.assertValueEqual(attack.windup(), windup, type + "'s windup");
		helper.assertValueEqual(attack.cooldown(), cooldown, type + "'s cooldown");
	}

	// The mob, holding the attack key, looking along x; the test ticks the morphs
	private static MockPlayer attacker(GameTestHelper helper, EntityType<?> type) {
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, type, OptionalInt.empty());
		// A living thing looks where its head turns
		player.setYRot(-90);
		player.setYHeadRot(-90);
		player.setXRot(0);
		Attacks.held(player, true);
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		return player;
	}

	private static void end(GameTestHelper helper, MockPlayer player) {
		Attacks.held(player, false);
		Morph.end(player);
		helper.succeed();
	}

	// An iron golem that stays put, on the floor, that many blocks ahead along the look; the
	// test's barriers stand 8 blocks from its corner, and stop the look
	private static IronGolem target(GameTestHelper helper, int ahead) {
		helper.setBlock(1 + ahead, 0, 1, Blocks.STONE);
		return helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, 1.5F + ahead, 1, 1.5F);
	}

	private static List<EvokerFangs> fangs(GameTestHelper helper, ServerPlayer owner) {
		AABB around = new AABB(owner.position(), owner.position()).inflate(24);
		return helper.getLevel().getEntities(EntityTypes.EVOKER_FANGS, around, fang -> fang.getOwner() == owner);
	}

	// A pig the morph's melee hit just landed on, beside the player
	private static Pig hit(GameTestHelper helper, MockPlayer player, EntityType<?> type) {
		Morph.start(player, type, OptionalInt.empty());
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, 2.5F, 1, 1.5F);
		Hits.afterDamage(pig, player.damageSources().playerAttack(player), 1, 1, false);
		Morph.end(player);
		return pig;
	}

	private static void effect(GameTestHelper helper, LivingEntity entity, Holder<MobEffect> effect, int ticks, String what) {
		MobEffectInstance instance = entity.getEffect(effect);
		if (ticks <= 0) {
			helper.assertTrue(instance == null, what + " hit added an effect on this difficulty");
			return;
		}
		helper.assertTrue(instance != null, what + " hit added no " + effect.getRegisteredName());
		helper.assertValueEqual(instance.getDuration(), ticks, what + " hit's " + effect.getRegisteredName());
	}

	// Seconds of poison: none on Easy
	private static int byDifficulty(Difficulty difficulty, int normal, int hard) {
		return switch (difficulty) {
			case NORMAL -> normal * 20;
			case HARD -> hard * 20;
			default -> 0;
		};
	}

	// Neither player hurt nor pushed: the host's own attacks never hurt them, and the other player is spared
	private static void spared(GameTestHelper helper, ServerPlayer host, ServerPlayer guest, String what) {
		helper.assertTrue(host.getHealth() == host.getMaxHealth(), what + " hurt the host");
		helper.assertTrue(guest.getHealth() == guest.getMaxHealth(), what + " hurt another player");
		helper.assertTrue(guest.getDeltaMovement().lengthSqr() == 0, what + " pushed another player");
	}
}
