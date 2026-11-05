package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.Attacks;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Morph;
import com.ialfaro.tokio.game.Spawned;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The morphs' attacks that shoot or throw something ({@code game/Shots}), on
 * mock players holding the attack key. Each test ticks {@link Morph#tick}
 * itself, takes each shot out of the world as it comes, and turns its players
 * back at the end, so their keys don't fire on in other tests.
 */
public class ShotsTest {
	private static final double CLOSE = 1.0E-4;

	@GameTest(maxTicks = 60)
	public void witherFiresFromEachHeadInTurn(GameTestHelper helper) {
		MockPlayer player = morphed(helper, EntityTypes.WITHER);
		List<Shot> skulls = fired(helper, player, EntityTypes.WITHER_SKULL);
		helper.runAfterDelay(40, () -> done(helper, () -> {
			ticks(helper, skulls, List.of(0L, 15L, 30L), "the skulls");
			ours(helper, player, skulls);
			// The middle head on top, then the side heads, 1.3 blocks to either side
			Vec3 middle = skulls.get(0).entity.position().subtract(player.position());
			Vec3 right = skulls.get(1).entity.position().subtract(player.position());
			Vec3 left = skulls.get(2).entity.position().subtract(player.position());
			near(helper, middle.y, 3.0, "the middle head's height");
			near(helper, right.y, 2.2, "a side head's height");
			near(helper, right.x - left.x, 2.6, "the side heads' distance apart");
		}, player));
	}

	@GameTest(maxTicks = 40)
	public void ghastFiresAfterItsWarning(GameTestHelper helper) {
		MockPlayer player = morphed(helper, EntityTypes.GHAST);
		List<Shot> fireballs = fired(helper, player, EntityTypes.FIREBALL);
		helper.runAfterDelay(30, () -> done(helper, () -> {
			ticks(helper, fireballs, List.of(20L), "the fireball");
			ours(helper, player, fireballs);
			// 4 blocks ahead, along the look
			Vec3 from = fireballs.getFirst().entity.position();
			helper.assertTrue(from.z - player.getZ() > 3.9, "The fireball didn't leave 4 blocks ahead: " + (from.z - player.getZ()));
		}, player));
	}

	@GameTest(maxTicks = 130)
	public void blazeFiresThreeThenRests(GameTestHelper helper) {
		MockPlayer player = morphed(helper, EntityTypes.BLAZE);
		List<Shot> fireballs = fired(helper, player, EntityTypes.SMALL_FIREBALL);
		helper.runAfterDelay(115, () -> done(helper, () -> {
			ticks(helper, fireballs, List.of(0L, 6L, 12L, 112L), "the fireballs");
			ours(helper, player, fireballs);
		}, player));
	}

	@GameTest(maxTicks = 40)
	public void everyFamilyShootsItsOwn(GameTestHelper helper) {
		List<MockPlayer> players = new ArrayList<>();
		List<List<Shot>> shots = new ArrayList<>();
		List<EntityType<?>> mobs = List.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED, EntityTypes.ILLUSIONER,
				EntityTypes.PILLAGER, EntityTypes.PIGLIN, EntityTypes.DROWNED, EntityTypes.SNOW_GOLEM, EntityTypes.LLAMA, EntityTypes.TRADER_LLAMA,
				EntityTypes.BREEZE, EntityTypes.ENDER_DRAGON);
		List<EntityType<?>> projectiles = List.of(EntityTypes.ARROW, EntityTypes.ARROW, EntityTypes.ARROW, EntityTypes.ARROW, EntityTypes.ARROW,
				EntityTypes.ARROW, EntityTypes.ARROW, EntityTypes.TRIDENT, EntityTypes.SNOWBALL, EntityTypes.LLAMA_SPIT, EntityTypes.LLAMA_SPIT,
				EntityTypes.BREEZE_WIND_CHARGE, EntityTypes.DRAGON_FIREBALL);
		for (int i = 0; i < mobs.size(); i++) {
			MockPlayer player = morphed(helper, mobs.get(i));
			players.add(player);
			shots.add(fired(helper, player, projectiles.get(i)));
		}
		// Before the second shots of those that wait 20
		helper.runAfterDelay(18, () -> done(helper, () -> {
			for (int i = 0; i < mobs.size(); i++) {
				List<Shot> fired = shots.get(i);
				EntityType<?> mob = mobs.get(i);
				// The breeze inhales first
				ticks(helper, fired, List.of(mob == EntityTypes.BREEZE ? 15L : 0L), mob + "'s shot");
				ours(helper, players.get(i), fired);
				if (fired.getFirst().entity instanceof AbstractArrow arrow) {
					helper.assertTrue(arrow.pickup == AbstractArrow.Pickup.DISALLOWED, mob + "'s " + arrow.getType() + " can be picked up");
				}
			}
			tipped(helper, shots.get(0), null, "The skeleton's arrow");
			tipped(helper, shots.get(1), MobEffects.SLOWNESS, "The stray's arrow");
			tipped(helper, shots.get(2), MobEffects.POISON, "The bogged's arrow");
			tipped(helper, shots.get(3), MobEffects.WEAKNESS, "The parched's arrow");
			AbstractArrow bolt = (AbstractArrow) shots.get(5).getFirst().entity;
			helper.assertTrue(bolt.getWeaponItem() != null && bolt.getWeaponItem().is(Items.CROSSBOW), "The pillager's arrow isn't from a crossbow");
		}, players.toArray(ServerPlayer[]::new)));
	}

	@GameTest(maxTicks = 20)
	public void witchPicksItsPotion(GameTestHelper helper) {
		helper.setBlock(1, 0, 4, Blocks.STONE);
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(1.5, 1, 4.5));
		MockPlayer aiming = morphed(helper, EntityTypes.WITCH);
		look(aiming, pig.getBoundingBox().getCenter());
		MockPlayer away = morphed(helper, EntityTypes.WITCH);
		away.setYRot(180);
		away.setYHeadRot(180);
		List<Shot> atThePig = fired(helper, aiming, EntityTypes.SPLASH_POTION);
		List<Shot> atNothing = fired(helper, away, EntityTypes.SPLASH_POTION);
		helper.runAfterDelay(5, () -> done(helper, () -> {
			ticks(helper, atThePig, List.of(0L), "the potion at the pig");
			ticks(helper, atNothing, List.of(0L), "the potion at nothing");
			ours(helper, aiming, atThePig);
			// A healthy pig 3 blocks away gets Poison; with nothing aimed at, Harming
			potion(helper, atThePig, Potions.POISON, "The potion at the pig");
			potion(helper, atNothing, Potions.HARMING, "The potion at nothing");
			pig.discard();
		}, aiming, away));
	}

	@GameTest(maxTicks = 20)
	public void shulkerNeedsATarget(GameTestHelper helper) {
		helper.setBlock(1, 0, 4, Blocks.STONE);
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(1.5, 1, 4.5));
		MockPlayer aiming = morphed(helper, EntityTypes.SHULKER);
		look(aiming, pig.getBoundingBox().getCenter());
		MockPlayer away = morphed(helper, EntityTypes.SHULKER);
		away.setYRot(180);
		away.setYHeadRot(180);
		List<Shot> atThePig = fired(helper, aiming, EntityTypes.SHULKER_BULLET);
		List<Shot> atNothing = fired(helper, away, EntityTypes.SHULKER_BULLET);
		helper.runAfterDelay(10, () -> done(helper, () -> {
			ticks(helper, atThePig, List.of(0L), "the bullet at the pig");
			ours(helper, aiming, atThePig);
			helper.assertTrue(atNothing.isEmpty(), "A shulker fired at nothing");
			pig.discard();
		}, aiming, away));
	}

	// Only the host: what a morphed player fires passes other players by, and never hits the player
	@GameTest
	public void shotsFlyThroughOtherPlayersAndTheShooter(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			// Both clear of the shooter: until then, the game's own check spares them
			Arrow arrow = leftTheShooter((Arrow) fireOnce(helper, host, EntityTypes.SKELETON, EntityTypes.ARROW));
			helper.assertFalse(canHit(arrow, guest), "A morphed player's arrow can hit another player");
			helper.assertFalse(canHit(arrow, host), "A morphed player's arrow can hit them");
			Arrow control = leftTheShooter(new Arrow(helper.getLevel(), host, new ItemStack(Items.ARROW), null));
			helper.assertTrue(canHit(control, guest), "A player's own arrow can't hit another player");
			helper.assertTrue(canHit(control, host), "A player's own arrow can't hit them");
		});
	}

	@GameTest(padding = 16)
	public void blastsSpareOtherPlayersAndTheShooter(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			blast(helper, fireOnce(helper, host, EntityTypes.GHAST, EntityTypes.FIREBALL));
			spared(helper, host, guest, "A morphed player's ghast fireball");
			blast(helper, fireOnce(helper, host, EntityTypes.WITHER, EntityTypes.WITHER_SKULL));
			spared(helper, host, guest, "A morphed player's wither skull");
			// A fireball that isn't Tokio's, the host's own, hurts them both
			blast(helper, new LargeFireball(helper.getLevel(), host, new Vec3(0, 0, 1), 1));
			hurt(helper, host, guest, "The host's own fireball");
		});
	}

	@GameTest
	public void potionsSpareOtherPlayersAndTheThrower(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			ThrownSplashPotion potion = (ThrownSplashPotion) fireOnce(helper, host, EntityTypes.WITCH, EntityTypes.SPLASH_POTION);
			splash(helper, potion);
			spared(helper, host, guest, "A morphed player's potion");
			// Tokio's witch's splashes the host alone
			ok(helper, host, "summon minecraft:witch ~ ~ ~");
			Witch witch = helper.findOneEntity(EntityTypes.WITCH);
			splash(helper, new ThrownSplashPotion(helper.getLevel(), witch, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)));
			witch.discard();
			helper.assertTrue(host.getHealth() < host.getMaxHealth(), "Tokio's witch's potion didn't reach the host");
			helper.assertTrue(guest.getHealth() == guest.getMaxHealth(), "Tokio's witch's potion hurt another player");
			heal(host);
			splash(helper, new ThrownSplashPotion(helper.getLevel(), host, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)));
			hurt(helper, host, guest, "The host's own potion");
		});
	}

	@GameTest
	public void dragonsBreathSparesOtherPlayersAndTheDragon(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			AreaEffectCloud breath = breathe(helper, (DragonFireball) fireOnce(helper, host, EntityTypes.ENDER_DRAGON, EntityTypes.DRAGON_FIREBALL));
			helper.assertTrue(Spawned.is(breath), "A morphed player's dragon breath isn't Tokio's");
			spared(helper, host, guest, "A morphed player's dragon breath");
			breathe(helper, new DragonFireball(helper.getLevel(), host, new Vec3(0, 0, 1)));
			hurt(helper, host, guest, "The host's own dragon breath");
		});
	}

	// A mock player turned into the mob
	private static MockPlayer morphed(GameTestHelper helper, EntityType<?> type) {
		MockPlayer player = new MockPlayer(helper);
		helper.assertTrue(Morph.start(player, type, OptionalInt.empty()), "Couldn't turn into " + type);
		return player;
	}

	private record Shot(long tick, Entity entity) {
	}

	// Holds the player's key from now; each shot of that kind they fire is
	// taken out of the world at once, with its tick counted from the attack's
	// start: its first shot, or its windup's. Morph may tick a test's players
	// before the test starts in a tick, or after, so that's this tick or the next.
	private static List<Shot> fired(GameTestHelper helper, ServerPlayer player, EntityType<?> type) {
		List<Shot> shots = new ArrayList<>();
		long held = helper.getTick();
		long[] start = {-1};
		Attacks.held(player, true);
		helper.onEachTick(() -> {
			Morph.tick(helper.getLevel().getServer());
			List<? extends Entity> made = helper.getLevel().getEntities(type, player.getBoundingBox().inflate(32), entity -> ownedBy(entity, player));
			if (start[0] < 0 && (!made.isEmpty() || player.hasAttached(Attacks.WINDUP))) {
				start[0] = helper.getTick();
				helper.assertTrue(start[0] - held <= 1, "The attack started " + (start[0] - held) + " ticks after the key went down");
			}
			for (Entity entity : made) {
				shots.add(new Shot(helper.getTick() - start[0], entity));
				entity.discard();
			}
		});
		return shots;
	}

	private static boolean ownedBy(Entity entity, ServerPlayer player) {
		return entity instanceof TraceableEntity traceable && traceable.getOwner() == player;
	}

	// Runs the checks, then lets the key go and turns the players back, pass or fail
	private static void done(GameTestHelper helper, Runnable checks, ServerPlayer... players) {
		try {
			checks.run();
		} finally {
			for (ServerPlayer player : players) {
				Attacks.held(player, false);
				Morph.end(player);
			}
		}
		helper.succeed();
	}

	private static void ticks(GameTestHelper helper, List<Shot> shots, List<Long> expected, String what) {
		helper.assertValueEqual(shots.stream().map(Shot::tick).toList(), expected, "the ticks of " + what);
	}

	// The player owns each shot, and it's Tokio's
	private static void ours(GameTestHelper helper, ServerPlayer player, List<Shot> shots) {
		for (Shot shot : shots) {
			helper.assertTrue(Spawned.is(shot.entity), shot.entity.getType() + " isn't tagged");
		}
		helper.assertTrue(!shots.isEmpty() && ownedBy(shots.getFirst().entity, player), "The player doesn't own their shot");
	}

	private static void tipped(GameTestHelper helper, List<Shot> shots, Holder<MobEffect> effect, String what) {
		PotionContents contents = ((AbstractArrow) shots.getFirst().entity).getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS,
				PotionContents.EMPTY);
		List<MobEffectInstance> effects = new ArrayList<>();
		contents.getAllEffects().forEach(effects::add);
		if (effect == null) {
			helper.assertTrue(effects.isEmpty(), what + " is tipped: " + effects);
		} else {
			helper.assertTrue(effects.size() == 1 && effects.getFirst().is(effect), what + " isn't tipped with " + effect.getRegisteredName() + ": " + effects);
		}
	}

	private static void potion(GameTestHelper helper, List<Shot> shots, Holder<Potion> potion, String what) {
		ThrownSplashPotion thrown = (ThrownSplashPotion) shots.getFirst().entity;
		PotionContents contents = thrown.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		helper.assertTrue(contents.is(potion), what + " isn't " + potion.getRegisteredName() + ": " + contents);
	}

	// Turns the player's look to that point (ServerPlayer.lookAt tells a client)
	private static void look(ServerPlayer player, Vec3 at) {
		Vec3 d = at.subtract(player.getEyePosition());
		float yRot = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		player.setYRot(yRot);
		player.setYHeadRot(yRot);
		player.setXRot((float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < CLOSE, what + " is " + actual + ", not " + expected);
	}

	// The host and another player in the world, 2 blocks apart on a floor, while the action runs, as in HostOnlyTest
	private static void hosted(GameTestHelper helper, BiConsumer<ServerPlayer, ServerPlayer> action) {
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) helper.setBlock(x, 0, z, Blocks.OBSIDIAN);
		}
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer host = HostOnlyTest.inTheWorld(helper, new Vec3(2.5, 1, 3.5));
		ServerPlayer guest = HostOnlyTest.inTheWorld(helper, new Vec3(4.5, 1, 3.5));
		server.setSingleplayerProfile(host.getGameProfile());
		try {
			action.accept(host, guest);
		} finally {
			server.setSingleplayerProfile(null);
			server.getPlayerList().remove(host);
			server.getPlayerList().remove(guest);
		}
		helper.succeed();
	}

	// The mob's attack, fired once by the host as is, and what it made
	private static Entity fireOnce(GameTestHelper helper, ServerPlayer host, EntityType<?> mob, EntityType<?> type) {
		Attacks.of(mob).fire(host, null);
		List<? extends Entity> made = helper.getLevel().getEntities(type, host.getBoundingBox().inflate(16), entity -> ownedBy(entity, host));
		helper.assertTrue(made.size() == 1, "The " + mob + "'s attack made " + made.size() + " " + type);
		return made.getFirst();
	}

	// Between the players
	private static Vec3 between(GameTestHelper helper) {
		return helper.absoluteVec(new Vec3(3.5, 1, 3.5));
	}

	// A power 2 blast between the players, set off by the projectile, which breaks nothing, as HostOnlyTest's
	private static void blast(GameTestHelper helper, Entity source) {
		Vec3 at = between(helper);
		helper.getLevel().explode(source, at.x, at.y, at.z, 2, Level.ExplosionInteraction.NONE);
		source.discard();
	}

	private static void splash(GameTestHelper helper, ThrownSplashPotion potion) {
		Vec3 at = between(helper);
		potion.onHitAsPotion(helper.getLevel(), potion.getItem(), new BlockHitResult(at, Direction.UP, BlockPos.containing(at), false));
		potion.discard();
	}

	// The fireball lands between the players, and its cloud waits its 20 ticks and breathes on them
	private static AreaEffectCloud breathe(GameTestHelper helper, DragonFireball fireball) {
		ServerLevel level = helper.getLevel();
		Vec3 at = between(helper);
		fireball.setPos(at);
		hit(fireball, new BlockHitResult(at, Direction.UP, BlockPos.containing(at).below(), false));
		AreaEffectCloud cloud = level.getEntities(EntityTypes.AREA_EFFECT_CLOUD, fireball.getBoundingBox().inflate(8), entity -> true).getFirst();
		for (int tick = 0; tick < 30 && !cloud.isRemoved(); tick++) {
			cloud.commonTick();
			cloud.tick();
		}
		cloud.discard();
		return cloud;
	}

	private static void spared(GameTestHelper helper, ServerPlayer host, ServerPlayer guest, String what) {
		for (ServerPlayer player : List.of(host, guest)) {
			String who = player == guest ? "another player" : "the host";
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), what + " hurt " + who);
			helper.assertTrue(player.getDeltaMovement().lengthSqr() == 0, what + " pushed " + who);
		}
	}

	// Both took the hit; they heal for the next
	private static void hurt(GameTestHelper helper, ServerPlayer host, ServerPlayer guest, String what) {
		for (ServerPlayer player : List.of(host, guest)) {
			helper.assertTrue(player.getHealth() < player.getMaxHealth(), what + " didn't hurt " + (player == guest ? "another player" : "the host"));
			heal(player);
		}
	}

	// Full health, still, and open to the next hit at once, which the game's hurt cooldown would cut
	private static void heal(ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.setDeltaMovement(Vec3.ZERO);
		player.damageCooldownTime = 0;
	}

	// Projectile.canHitEntity, onHit and leftOwner aren't public
	private static boolean canHit(Projectile projectile, Entity entity) {
		try {
			Method canHit = Projectile.class.getDeclaredMethod("canHitEntity", Entity.class);
			canHit.setAccessible(true);
			return (boolean) canHit.invoke(projectile, entity);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static <T extends Projectile> T leftTheShooter(T projectile) {
		try {
			Field leftOwner = Projectile.class.getDeclaredField("leftOwner");
			leftOwner.setAccessible(true);
			leftOwner.setBoolean(projectile, true);
			return projectile;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void hit(Projectile projectile, HitResult result) {
		try {
			Method onHit = Projectile.class.getDeclaredMethod("onHit", HitResult.class);
			onHit.setAccessible(true);
			onHit.invoke(projectile, result);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void ok(GameTestHelper helper, ServerPlayer player, String commandLine) {
		CommandResult result = new CommandRunner(helper.getLevel().getServer(), () -> player).run(commandLine).getNow(null);
		helper.assertTrue(result.ok(), "/" + commandLine + " failed: " + result.message());
	}
}
