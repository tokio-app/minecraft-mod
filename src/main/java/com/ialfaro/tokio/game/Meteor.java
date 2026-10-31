package com.ialfaro.tokio.game;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Meteors from high above: a tumbling magma rock with a trail of fire and
 * smoke, which explodes where the player stood and leaves a lump of rock. It
 * flies through anything in its way, so a roof or a cave doesn't stop it.
 */
public final class Meteor extends Hazard {
	public static final int MAX_COUNT = 100;
	// Where a meteor starts, above and away from where it lands, so it streaks in at an angle
	private static final double HEIGHT = 100;
	private static final double AWAY = 25;
	// How long it falls: the player's time to run
	private static final int FALL_TICKS = 40;
	// A shower's other meteors land this close to the player, at most this late
	private static final double SPREAD = 8;
	private static final int MAX_DELAY_TICKS = 60;
	private static final float SIZE = 2.5F;
	private static final float POWER = 5;
	// How far down the crater's floor can be for the rock to land on it
	private static final int ROCK_DEPTH = 6;

	private final Vec3 start;
	private final Vec3 target;
	// Ticks left before it appears, and since it did
	private int delay;
	private int flown;

	private Meteor(ServerLevel level, Vec3 start, Vec3 target, int delay) {
		super(level);
		this.start = start;
		this.target = target;
		this.delay = delay;
		setPos(start);
		// Smooths each tick's move and turn on the client
		setPosRotInterpolationDuration(2);
		setTransformationInterpolationDuration(1);
		setTransformation(turned(0));
		// Seen from the ground while it's still 100 blocks up
		setViewRange(4);
	}

	/**
	 * Sends {@code count} meteors at the player. The first lands on the ground
	 * under them, the rest around them, one after another.
	 */
	public static List<Meteor> fall(ServerPlayer player, int count) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		List<Meteor> meteors = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Vec3 spot = player.position();
			int delay = 0;
			if (i > 0) {
				// Anywhere on the disk around the player, as likely
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = SPREAD * Math.sqrt(random.nextDouble());
				spot = spot.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
				delay = 1 + random.nextInt(MAX_DELAY_TICKS);
			}
			// A player up in the air gets it on the ground below
			Vec3 target = groundUnder(level, spot);
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 start = target.add(Math.cos(angle) * AWAY, HEIGHT, Math.sin(angle) * AWAY);
			Meteor meteor = new Meteor(level, start, target, delay);
			if (delay == 0) meteor.appear();
			level.addFreshEntity(meteor);
			meteors.add(meteor);
		}
		return meteors;
	}

	@Override
	public void tick() {
		super.tick();
		if (delay > 0) {
			if (--delay == 0) appear();
			return;
		}
		flown++;
		Vec3 at = start.lerp(target, Math.min(1, flown / (double) FALL_TICKS));
		setPos(at);
		setTransformation(turned(flown));
		setTransformationInterpolationDelay(0);
		trail(at);
		if (flown >= FALL_TICKS) land();
	}

	// Until then it's air, which draws nothing
	private void appear() {
		setBlockState(Blocks.MAGMA_BLOCK.defaultBlockState());
		serverLevel().playSound(null, target.x, target.y, target.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 4, 0.5F);
	}

	// Sent to players up to 512 blocks away, as it's far up
	private void trail(Vec3 at) {
		ServerLevel level = serverLevel();
		level.sendParticles(ParticleTypes.FLAME, true, false, at.x, at.y, at.z, 12, 0.6, 0.6, 0.6, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, at.x, at.y, at.z, 6, 0.5, 0.5, 0.5, 0.01);
		level.sendParticles(ParticleTypes.LAVA, true, false, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
	}

	private void land() {
		blast(target, POWER);
		serverLevel().sendParticles(ParticleTypes.LAVA, true, false, target.x, target.y, target.z, 30, 1.5, 0.5, 1.5, 0);
		leaveRock();
		discard();
	}

	// A magma core with some blackstone around it, on the crater's floor
	private void leaveRock() {
		ServerLevel level = serverLevel();
		BlockPos core = BlockPos.containing(target);
		for (int depth = 0; depth < ROCK_DEPTH && level.getBlockState(core.below()).canBeReplaced(); depth++) {
			core = core.below();
		}
		// Nothing to land on: the player was in the air
		if (level.getBlockState(core.below()).canBeReplaced()) return;
		place(core, Blocks.MAGMA_BLOCK);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextBoolean()) place(core.relative(side), Blocks.BLACKSTONE);
		}
		if (random.nextBoolean()) place(core.above(), Blocks.BLACKSTONE);
	}

	// Only where it takes nothing away, like air, fire or a plant
	private void place(BlockPos pos, Block block) {
		if (serverLevel().getBlockState(pos).canBeReplaced()) serverLevel().setBlockAndUpdate(pos, block.defaultBlockState());
	}

	// The rock turned around its center
	private static Transformation turned(int tick) {
		Quaternionf rotation = new Quaternionf().rotationXYZ(tick * 0.21F, tick * 0.33F, tick * 0.13F);
		Vector3f corner = rotation.transform(new Vector3f(-SIZE / 2));
		return new Transformation(corner, rotation, new Vector3f(SIZE), null);
	}
}
