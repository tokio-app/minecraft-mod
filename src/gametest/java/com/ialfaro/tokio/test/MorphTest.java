package com.ialfaro.tokio.test;

import com.ialfaro.tokio.game.Morph;
import com.ialfaro.tokio.mixin.LivingEntityInvoker;
import java.util.List;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

/**
 * Morphs on a mock player. Game tests have no world host, who alone has
 * /tokio, so they start and end the morphs themselves, and tick
 * {@link Morph#tick}: the test server never runs Tokio's initializer.
 */
public class MorphTest {
	private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("tokio", "morph");
	private static final double CLOSE = 1.0E-4;

	@GameTest
	public void ironGolemTakesItsSizeStatsAndSounds(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		player.setHealth(10);
		helper.assertTrue(Morph.start(player, EntityTypes.IRON_GOLEM, OptionalInt.empty()), "Couldn't turn into an iron golem");
		near(helper, player.getBbWidth(), 1.4, "the width");
		near(helper, player.getBbHeight(), 2.7, "the height");
		near(helper, player.getEyeHeight(), 2.295, "the eye height");
		near(helper, player.getBoundingBox().getXsize(), 1.4, "the bounding box's width");
		near(helper, player.getHealth(), 50, "the health");
		near(helper, player.getMaxHealth(), 100, "the max health");
		near(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 15, "the damage");
		near(helper, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1, "the knockback resistance");
		near(helper, player.getAttributeValue(Attributes.STEP_HEIGHT), 1, "the step height");
		near(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.25 * 0.25 / 0.98, "the speed");
		near(helper, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 4.5 * 1.5, "the block reach");
		near(helper, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 3 * 1.5, "the entity reach");
		near(helper, player.getAttributeValue(Attributes.CAMERA_DISTANCE), 4 * 1.5, "the camera distance");
		helper.assertValueEqual(Morph.look(player), EntityTypes.IRON_GOLEM, "the look");
		LivingEntityInvoker sounds = (LivingEntityInvoker) (Object) player;
		helper.assertValueEqual(sounds.tokio$getHurtSound(player.damageSources().generic()), SoundEvents.IRON_GOLEM_HURT, "the hurt sound");
		helper.assertValueEqual(sounds.tokio$getDeathSound(), SoundEvents.IRON_GOLEM_DEATH, "the death sound");

		Morph.end(player);
		near(helper, player.getBbWidth(), 0.6, "the width after");
		near(helper, player.getBbHeight(), 1.8, "the height after");
		near(helper, player.getHealth(), 10, "the health after");
		near(helper, player.getMaxHealth(), 20, "the max health after");
		helper.assertTrue(Morph.look(player) == null, "Still a mob after the end");
		helper.assertValueEqual(sounds.tokio$getDeathSound(), SoundEvents.PLAYER_DEATH, "the death sound after");
		helper.succeed();
	}

	@GameTest(maxTicks = 1100)
	public void lastsWithoutSeconds(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, EntityTypes.IRON_GOLEM, OptionalInt.empty());
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.runAfterDelay(1000, () -> {
			helper.assertValueEqual(Morph.look(player), EntityTypes.IRON_GOLEM, "the look after 1,000 ticks");
			Morph.end(player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void endsAfterItsSeconds(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, EntityTypes.IRON_GOLEM, OptionalInt.of(1));
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.runAfterDelay(15, () -> helper.assertValueEqual(Morph.look(player), EntityTypes.IRON_GOLEM, "the look after 15 ticks"));
		helper.runAfterDelay(22, () -> {
			helper.assertTrue(Morph.look(player) == null, "Still a mob after 22 ticks");
			near(helper, player.getMaxHealth(), 20, "the max health after");
			helper.succeed();
		});
	}

	@GameTest
	public void everyMobComesAndGoes(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		int mobs = 0;
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (!Morph.canBecome(type)) continue;
			mobs++;
			helper.assertTrue(Morph.start(player, type, OptionalInt.empty()), "Couldn't turn into " + type);
			helper.assertValueEqual(Morph.look(player), type, "the look");
			Morph.end(player);
			near(helper, player.getBbWidth(), 0.6, "the width after " + type);
			near(helper, player.getBbHeight(), 1.8, "the height after " + type);
			near(helper, player.getMaxHealth(), 20, "the max health after " + type);
			helper.assertFalse(player.getAbilities().mayfly, "The player can still fly after " + type);
			for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.asHolderIdMap()) {
				AttributeInstance instance = player.getAttribute(attribute);
				helper.assertFalse(instance != null && instance.hasModifier(MODIFIER), "A modifier is left on " + attribute.getRegisteredName() + " after " + type);
			}
		}
		// Tokio's Java list has 90 living mobs
		helper.assertTrue(mobs >= 90, "Only " + mobs + " mobs");
		helper.succeed();
	}

	@GameTest
	public void refusesWhatIsntAMob(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		for (EntityType<?> type : new EntityType<?>[] {EntityTypes.TNT, EntityTypes.ARMOR_STAND, EntityTypes.PLAYER}) {
			helper.assertFalse(Morph.canBecome(type), type + " is offered");
			helper.assertFalse(Morph.start(player, type, OptionalInt.empty()), "Turned into " + type);
		}
		helper.assertTrue(Morph.look(player) == null, "Turned into what isn't a mob");
		helper.succeed();
	}

	@GameTest
	public void walksAtTheMobsSpeedWithinLimits(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		speed(helper, player, EntityTypes.COW, 0.05);
		speed(helper, player, EntityTypes.VILLAGER, 0.15);
		speed(helper, player, EntityTypes.COD, 0.05);
		speed(helper, player, EntityTypes.BAT, 0.1);
		helper.succeed();
	}

	@GameTest
	public void flyersFlyAndChickensFallSlowly(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		player.setOnGround(true);
		Morph.start(player, EntityTypes.BAT, OptionalInt.empty());
		helper.assertTrue(player.getAbilities().mayfly, "A bat can't fly");
		// Airborne at once: off the ground, as creative flight would land it
		helper.assertTrue(player.getAbilities().flying, "A bat isn't flying");
		helper.assertTrue(player.getDeltaMovement().y > 0, "A bat on the ground didn't take off");
		near(helper, player.getAbilities().getFlyingSpeed(), 0.05, "a bat's flying speed");
		Morph.start(player, EntityTypes.GHAST, OptionalInt.empty());
		helper.assertTrue(player.getAbilities().flying, "A bat turned ghast stopped flying");
		near(helper, player.getAbilities().getFlyingSpeed(), 0.025, "a ghast's flying speed");
		Morph.start(player, EntityTypes.PHANTOM, OptionalInt.empty());
		near(helper, player.getAbilities().getFlyingSpeed(), 0.075, "a phantom's flying speed");
		Morph.end(player);
		helper.assertFalse(player.getAbilities().mayfly, "The player still flies");
		helper.assertFalse(player.getAbilities().flying, "The player is still flying");
		near(helper, player.getAbilities().getFlyingSpeed(), 0.05, "the flying speed after");
		// A crash can leave a morph's flying speed in the player's save
		player.getAbilities().setFlyingSpeed(0.075F);
		Morph.joined(player);
		near(helper, player.getAbilities().getFlyingSpeed(), 0.05, "the flying speed a crash left");
		// The phantom ended in the air, so this player floats down for a while
		helper.assertTrue(player.getEffect(MobEffects.SLOW_FALLING) != null, "The phantom's player falls fast");
		MockPlayer hen = new MockPlayer(helper);
		Morph.start(hen, EntityTypes.CHICKEN, OptionalInt.empty());
		MobEffectInstance slowFalling = hen.getEffect(MobEffects.SLOW_FALLING);
		helper.assertTrue(slowFalling != null && !slowFalling.showIcon(), "A chicken has no hidden Slow Falling");
		Morph.end(hen);
		helper.assertTrue(hen.getEffect(MobEffects.SLOW_FALLING) == null, "The Slow Falling is still there");
		helper.succeed();
	}

	@GameTest
	public void fishDrownOnLandAndBreatheInWater(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		MockPlayer zombie = new MockPlayer(helper);
		Morph.start(player, EntityTypes.COD, OptionalInt.empty());
		Morph.start(zombie, EntityTypes.ZOMBIE, OptionalInt.empty());
		helper.assertTrue(player.getAttributeValue(Attributes.OXYGEN_BONUS) >= 1024, "A cod has no oxygen bonus");
		helper.assertTrue(zombie.getAttributeValue(Attributes.OXYGEN_BONUS) >= 1024, "A zombie has no oxygen bonus");
		Morph.end(zombie);
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(player.getAirSupply() < player.getMaxAirSupply() - 10, "A cod on land keeps its air: " + player.getAirSupply());
			Morph.end(player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void blazesCantBurn(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, EntityTypes.BLAZE, OptionalInt.empty());
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.runAfterDelay(200, () -> {
			MobEffectInstance fireResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
			helper.assertTrue(fireResistance != null && !fireResistance.showIcon(), "A blaze has no hidden Fire Resistance");
			helper.assertTrue(fireResistance.getDuration() <= 60, "The Fire Resistance lasts longer than 3 seconds: " + fireResistance.getDuration());
			Morph.end(player);
			helper.assertTrue(player.getEffect(MobEffects.FIRE_RESISTANCE) == null, "The Fire Resistance is still there");
			helper.succeed();
		});
	}

	@GameTest
	public void endermenAreHurtInWater(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.WATER);
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, EntityTypes.ENDERMAN, OptionalInt.empty());
		float health = player.getHealth();
		// The mock player isn't ticked by the world; this finds the water around them
		player.baseTick();
		helper.assertTrue(player.isInWater(), "The player isn't in the water");
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.succeedWhen(() -> {
			helper.assertTrue(player.getHealth() < health, "The water doesn't hurt an enderman");
			Morph.end(player);
		});
	}

	// In its own batch at noon: the super lasers in tokio-test:noon bring the night
	@GameTest(environment = "tokio-test:noon_morph", maxTicks = 400, skyAccess = true)
	public void zombiesBurnInTheSun(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, EntityTypes.ZOMBIE, OptionalInt.empty());
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.succeedWhen(() -> {
			helper.assertTrue(player.isOnFire(), "The zombie isn't burning");
			Morph.end(player);
		});
	}

	@GameTest
	public void spidersClimbWalls(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		player.horizontalCollision = true;
		helper.assertFalse(player.onClimbable(), "A player climbs a wall");
		Morph.start(player, EntityTypes.SPIDER, OptionalInt.empty());
		helper.assertTrue(player.onClimbable(), "A spider doesn't climb the wall it walks into");
		player.horizontalCollision = false;
		helper.assertFalse(player.onClimbable(), "A spider climbs without a wall");
		Morph.end(player);
		helper.succeed();
	}

	// Past 4×4 the game's search for room took minutes by real terrain and held the
	// world; the dragon rises over what's in its way instead
	@GameTest(padding = 16, skyAccess = true)
	public void bigMobsRiseUntilTheyFit(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		BlockPos feet = BlockPos.containing(player.position());
		// A wall 3 blocks high, inside the dragon's 16 blocks but clear of a player
		BlockPos.betweenClosed(feet.offset(3, 0, -2), feet.offset(3, 2, 2)).forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
		double y = player.getY();
		helper.assertTrue(Morph.start(player, EntityTypes.ENDER_DRAGON, OptionalInt.empty()), "Couldn't turn into the Ender Dragon");
		helper.assertTrue(helper.getLevel().noCollision(player), "The dragon is stuck in the wall");
		helper.assertTrue(player.getY() >= y + 3, "The dragon didn't rise over the wall: " + (player.getY() - y));
		near(helper, player.getMaxHealth(), 200, "the dragon's max health");
		Morph.end(player);
		helper.succeed();
	}

	// Like the real dragon: what its body flies into breaks, but not obsidian, and not the ground under it
	@GameTest(padding = 16, skyAccess = true)
	public void dragonSmashesWhatItFliesInto(GameTestHelper helper) {
		MockPlayer player = new MockPlayer(helper);
		Morph.start(player, EntityTypes.ENDER_DRAGON, OptionalInt.empty());
		BlockPos feet = BlockPos.containing(player.position());
		// Just past its body, 8 blocks from its middle: at its feet, at its head, and obsidian
		BlockPos side = feet.offset(9, 0, 0);
		BlockPos head = feet.offset(9, 7, 0);
		BlockPos immune = feet.offset(0, 0, 9);
		BlockPos ground = feet.below();
		for (BlockPos pos : List.of(side, head, ground)) helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(immune, Blocks.OBSIDIAN.defaultBlockState());
		helper.onEachTick(() -> Morph.tick(helper.getLevel().getServer()));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(side).isAir(), "The stone at its feet is still there");
			helper.assertTrue(helper.getLevel().getBlockState(head).isAir(), "The stone at its head is still there");
			helper.assertTrue(helper.getLevel().getBlockState(immune).is(Blocks.OBSIDIAN), "The dragon broke obsidian");
			helper.assertTrue(helper.getLevel().getBlockState(ground).is(Blocks.STONE), "The dragon broke the ground under it");
			Morph.end(player);
		});
	}

	private static void speed(GameTestHelper helper, MockPlayer player, EntityType<?> type, double expected) {
		Morph.start(player, type, OptionalInt.empty());
		near(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), expected, "the speed of " + type);
		Morph.end(player);
		near(helper, player.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.1, "the speed after " + type);
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < CLOSE, what + " is " + actual + ", not " + expected);
	}
}
