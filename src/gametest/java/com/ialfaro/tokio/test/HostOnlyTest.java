package com.ialfaro.tokio.test;

import com.ialfaro.tokio.connection.CommandResult;
import com.ialfaro.tokio.game.CommandRunner;
import com.ialfaro.tokio.game.Host;
import com.ialfaro.tokio.game.Laser;
import com.ialfaro.tokio.game.Meteor;
import com.ialfaro.tokio.game.Spawned;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * What Tokio's commands make reaches the world's host alone: a player beside
 * them isn't targeted, hurt or pushed, and never gets the hazards. Game tests
 * have no host, so each test makes one for the tick it runs in, and does all
 * it checks within that tick: no other test sees a host or these players.
 */
public class HostOnlyTest {
	// Each mob is aged past its grace, in which it goes after no one at all
	@GameTest
	public void tokiosMobsGoAfterTheHostAlone(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			ok(helper, host, "summon minecraft:zombie ~ ~ ~");
			Zombie zombie = helper.findOneEntity(EntityTypes.ZOMBIE);
			zombie.tickCount = Spawned.GRACE_TICKS;
			zombie.setTarget(guest);
			helper.assertTrue(zombie.getTarget() == null, "Tokio's zombie went after another player");
			zombie.setTarget(host);
			helper.assertTrue(zombie.getTarget() == host, "Tokio's zombie doesn't go after the host");
			// A brain's target goes through the same check
			ok(helper, host, "summon minecraft:piglin ~ ~ ~");
			Piglin piglin = helper.findOneEntity(EntityTypes.PIGLIN);
			piglin.tickCount = Spawned.GRACE_TICKS;
			piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, guest);
			helper.assertTrue(piglin.getTarget() == null, "Tokio's piglin went after another player");
			ok(helper, host, "summon minecraft:warden ~ ~ ~");
			Warden warden = helper.findOneEntity(EntityTypes.WARDEN);
			warden.tickCount = Spawned.GRACE_TICKS;
			helper.assertFalse(warden.canTargetEntity(guest), "Tokio's Warden can get angry at another player");
			helper.assertTrue(warden.canTargetEntity(host), "Tokio's Warden can't get angry at the host");
			// The world's own go after anyone
			Zombie control = helper.spawn(EntityTypes.ZOMBIE, 6, 1, 6);
			control.setTarget(guest);
			helper.assertTrue(control.getTarget() == guest, "A zombie Tokio didn't spawn doesn't go after another player");
		});
	}

	@GameTest(padding = 16)
	public void tokiosBlastsSpareOtherPlayers(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			// Tokio's TNT between them, set off at once
			ok(helper, host, "summon minecraft:tnt ~1 ~ ~ {fuse:80}");
			PrimedTnt tnt = helper.findOneEntity(EntityTypes.TNT);
			blast(helper, tnt);
			hurtOnlyTheHost(helper, host, guest, "Tokio's TNT");
			// A meteor of the mod's own, flown down at once
			Meteor meteor = Meteor.fall(host, 1).getFirst();
			for (int tick = 0; tick < 100 && !meteor.isRemoved(); tick++) meteor.tick();
			helper.assertTrue(meteor.isRemoved(), "The meteor didn't land");
			hurtOnlyTheHost(helper, host, guest, "The meteor");
			// The world's own TNT hurts anyone
			blast(helper, helper.spawn(EntityTypes.TNT, 3, 1, 3));
			helper.assertTrue(guest.getHealth() < guest.getMaxHealth(), "TNT Tokio didn't make spared another player");
		});
	}

	@GameTest
	public void tokiosLightningAndFangsStrikeTheHostAlone(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			ok(helper, host, "summon minecraft:lightning_bolt ~ ~ ~");
			LightningBolt bolt = helper.findOneEntity(EntityTypes.LIGHTNING_BOLT);
			bolt.tick();
			bolt.discard();
			hurtOnlyTheHost(helper, host, guest, "Tokio's lightning");
			// Fangs bite what stands on them: both, here
			guest.setPos(host.position());
			ok(helper, host, "summon minecraft:evoker_fangs ~ ~ ~");
			EvokerFangs fangs = helper.findOneEntity(EntityTypes.EVOKER_FANGS);
			for (int tick = 0; tick < 20 && !fangs.isRemoved(); tick++) fangs.tick();
			hurtOnlyTheHost(helper, host, guest, "Tokio's evoker fangs");
		});
	}

	@GameTest
	public void tokiosArrowsFlyThroughOtherPlayers(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			ok(helper, host, "summon minecraft:skeleton ~ ~ ~");
			Skeleton skeleton = helper.findOneEntity(EntityTypes.SKELETON);
			Arrow arrow = new Arrow(helper.getLevel(), skeleton, new ItemStack(Items.ARROW), null);
			helper.assertFalse(canHit(arrow, guest), "Tokio's skeleton's arrow can hit another player");
			helper.assertTrue(canHit(arrow, host), "Tokio's skeleton's arrow can't hit the host");
			Skeleton control = helper.spawn(EntityTypes.SKELETON, 6, 1, 6);
			helper.assertTrue(canHit(new Arrow(helper.getLevel(), control, new ItemStack(Items.ARROW), null), guest), "A skeleton Tokio didn't spawn can't shoot another player");
		});
	}

	@GameTest(padding = 16)
	public void onlyTheHostGetsTheHazards(GameTestHelper helper) {
		hosted(helper, (host, guest) -> {
			Meteor meteor = Meteor.fall(host, 1).getFirst();
			helper.assertTrue(meteor.broadcastToPlayer(host), "The host doesn't get their meteor");
			helper.assertFalse(meteor.broadcastToPlayer(guest), "Another player gets the host's meteor");
			meteor.discard();
			Laser laser = Laser.fire(host, Laser.LASER);
			helper.assertTrue(laser.broadcastToPlayer(host), "The host doesn't get their laser");
			helper.assertFalse(laser.broadcastToPlayer(guest), "Another player gets the host's laser");
			laser.discard();
		});
	}

	@GameTest
	public void particlesFollowThePlayersSetting(GameTestHelper helper) {
		RandomSource random = helper.getLevel().getRandom();
		helper.assertTrue(Host.particlesShown(12, ParticleStatus.ALL, random) == 12, "All doesn't show them all");
		helper.assertTrue(Host.particlesShown(12, ParticleStatus.MINIMAL, random) == 0, "Minimal shows some");
		int shown = 0;
		for (int i = 0; i < 300; i++) shown += Host.particlesShown(3, ParticleStatus.DECREASED, random);
		// Two in three of 900: 600, give or take 14
		helper.assertTrue(shown > 520 && shown < 680, "Decreased shows " + shown + " of 900, not about 600");
		helper.succeed();
	}

	// The host and another player in the world, on a floor, 2 blocks apart, while the action runs
	private static void hosted(GameTestHelper helper, BiConsumer<ServerPlayer, ServerPlayer> action) {
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) helper.setBlock(x, 0, z, Blocks.OBSIDIAN);
		}
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer host = inTheWorld(helper, new Vec3(2.5, 1, 3.5));
		ServerPlayer guest = inTheWorld(helper, new Vec3(4.5, 1, 3.5));
		// The chunks a meteor starts in: only the test's own are loaded
		ChunkPos chunk = host.chunkPosition();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) helper.getLevel().setChunkForced(chunk.x() + x, chunk.z() + z, true);
		}
		server.setSingleplayerProfile(host.getGameProfile());
		try {
			action.accept(host, guest);
		} finally {
			server.setSingleplayerProfile(null);
			// Out of the world, where they'd be the nearest players of other tests' mobs
			server.getPlayerList().remove(host);
			server.getPlayerList().remove(guest);
		}
		helper.succeed();
	}

	// A survival player the world's searches find, as blasts, bolts and fangs
	// do; the mock players aren't in it. Its connection goes nowhere. It takes
	// a blast at its feet without dying.
	static ServerPlayer inTheWorld(GameTestHelper helper, Vec3 at) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			// The game spares a player for 3 seconds after they join; these take the hits at once
			@Override
			public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
				return false;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		player.setHealth(player.getMaxHealth());
		player.setPos(helper.absoluteVec(at));
		return player;
	}

	// The host took the hit; the other player didn't, nor moved. The host heals for the next one.
	private static void hurtOnlyTheHost(GameTestHelper helper, ServerPlayer host, ServerPlayer guest, String what) {
		helper.assertTrue(host.getHealth() < host.getMaxHealth(), what + " didn't hurt the host");
		helper.assertTrue(guest.getHealth() == guest.getMaxHealth(), what + " hurt another player");
		helper.assertTrue(guest.getDeltaMovement().lengthSqr() == 0, what + " pushed another player");
		host.setHealth(host.getMaxHealth());
	}

	// Projectile.canHitEntity is protected
	private static boolean canHit(Projectile projectile, Entity entity) {
		try {
			Method canHit = Projectile.class.getDeclaredMethod("canHitEntity", Entity.class);
			canHit.setAccessible(true);
			return (boolean) canHit.invoke(projectile, entity);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void ok(GameTestHelper helper, ServerPlayer player, String commandLine) {
		// The test runs on the server thread, so the command runs at once
		CommandResult result = new CommandRunner(helper.getLevel().getServer(), () -> player).run(commandLine).getNow(null);
		helper.assertTrue(result.ok(), "/" + commandLine + " failed: " + result.message());
	}

	// A power 2 blast where the entity is, set off by it, which breaks nothing: 1 block from each player
	private static void blast(GameTestHelper helper, Entity source) {
		helper.getLevel().explode(source, source.getX(), source.getY(), source.getZ(), 2, Level.ExplosionInteraction.NONE);
		source.discard();
	}
}
