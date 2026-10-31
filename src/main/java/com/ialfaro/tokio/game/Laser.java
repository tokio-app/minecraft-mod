package com.ialfaro.tokio.game;

import com.mojang.math.Transformation;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A laser from far above: a thin red beam follows the player, holds still,
 * then comes down white-hot and blasts a hole where it stands.
 */
public final class Laser extends Hazard {
	// Following the player, then holding still: the time to run
	private static final int AIM_TICKS = 40;
	private static final int LOCK_TICKS = 20;
	// The white beam comes down from the sky in this many ticks
	private static final int DROP_TICKS = 4;
	// Blasts drilling down, this many ticks and blocks apart
	private static final int BLASTS = 3;
	private static final int BLAST_TICKS = 5;
	private static final int BLAST_STEP = 2;
	private static final float POWER = 4;
	private static final int FADE_TICKS = 6;
	private static final float AIM_WIDTH = 0.2F;
	private static final float FIRE_WIDTH = 2.5F;
	// Off vertical, so the beam leaves the player's feet beside their eyes
	// and they see it: one through the camera draws nothing
	private static final float TILT = (float) Math.toRadians(20);
	private static final int ABOVE_BUILD_LIMIT = 64;
	// The tick each part starts at
	private static final int FIRE = AIM_TICKS + LOCK_TICKS;
	private static final int FIRST_BLAST = FIRE + DROP_TICKS;
	private static final int FADE = FIRST_BLAST + BLASTS * BLAST_TICKS;
	private static final int END = FADE + FADE_TICKS;
	private static final DustParticleOptions RED = new DustParticleOptions(0xFF2020, 1.5F);
	private static final ColorParticleOption FLASH = ColorParticleOption.create(ParticleTypes.FLASH, 1F, 1F, 1F);

	private final ServerPlayer player;
	private final Quaternionf tilt;
	private final double top;
	private Vec3 target;
	private int age;

	private Laser(ServerPlayer player) {
		super(player.level());
		this.player = player;
		this.target = player.position();
		this.top = Math.max(player.level().getMaxY(), target.y) + ABOVE_BUILD_LIMIT;
		double side = random.nextDouble() * Math.PI * 2;
		this.tilt = new Quaternionf().rotationAxis(TILT, (float) Math.cos(side), 0, (float) Math.sin(side));
		setPos(target);
		setPosRotInterpolationDuration(1);
		setBlockState(Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
		setTransformation(beam(AIM_WIDTH, 0));
		// Its far end is hundreds of blocks away
		setViewRange(4);
	}

	/** Aims a laser at the player. */
	public static Laser fire(ServerPlayer player) {
		Laser laser = new Laser(player);
		player.level().addFreshEntity(laser);
		laser.sound(SoundEvents.GUARDIAN_ATTACK, 2, 0.8F);
		return laser;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (age < AIM_TICKS) follow();
		if (age < FIRE) ring();
		if (age == AIM_TICKS) sound(SoundEvents.BEACON_POWER_SELECT, 2, 1.5F);
		if (age == FIRE) {
			// White, and only a sliver at the top...
			setBlockState(Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
			reshape(beam(FIRE_WIDTH, length()), 0);
		}
		if (age == FIRE + 1) {
			// ...that comes down to the ground
			reshape(beam(FIRE_WIDTH, 0), DROP_TICKS);
			sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 5, 0.8F);
		}
		if (age >= FIRST_BLAST && age < FADE && (age - FIRST_BLAST) % BLAST_TICKS == 0) {
			int blast = (age - FIRST_BLAST) / BLAST_TICKS;
			Vec3 at = target.add(0, -blast * BLAST_STEP, 0);
			blast(at, POWER);
			sparks(at);
			if (blast == 0) {
				serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, true, false, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
				sound(SoundEvents.WARDEN_SONIC_BOOM, 3, 1);
			}
		}
		if (age == FADE) {
			reshape(beam(0, 0), FADE_TICKS);
			sound(SoundEvents.BEACON_DEACTIVATE, 2, 1);
		}
		if (age >= END) discard();
	}

	// Onto the player's feet, until they leave the world or its dimension
	private void follow() {
		if (player.isRemoved() || player.level() != level()) return;
		target = player.position();
		setPos(target);
		setTransformation(beam(AIM_WIDTH, 0));
	}

	// A red circle around where the beam lands, every other tick
	private void ring() {
		if (age % 2 != 0) return;
		for (int i = 0; i < 12; i++) {
			double angle = i * Math.PI / 6;
			serverLevel().sendParticles(RED, true, false, target.x + Math.cos(angle) * 1.5, target.y + 0.1, target.z + Math.sin(angle) * 1.5, 1, 0, 0, 0, 0);
		}
	}

	private void sparks(Vec3 at) {
		serverLevel().sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y + 1, at.z, 40, 1, 1, 1, 0.3);
		serverLevel().sendParticles(FLASH, true, false, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
	}

	private void sound(SoundEvent sound, float volume, float pitch) {
		serverLevel().playSound(null, target.x, target.y, target.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	// Animated on the client over the ticks given
	private void reshape(Transformation shape, int ticks) {
		setTransformationInterpolationDuration(ticks);
		setTransformation(shape);
		setTransformationInterpolationDelay(0);
	}

	// From the target up to the top, along the tilt
	private double length() {
		return (top - target.y) / Math.cos(TILT);
	}

	/** The beam, {@code width} wide, from {@code gap} blocks above the target up to the top. */
	private Transformation beam(float width, double gap) {
		Vector3f corner = tilt.transform(new Vector3f(-width / 2, (float) gap, -width / 2));
		return new Transformation(corner, new Quaternionf(tilt), new Vector3f(width, (float) (length() - gap), width), null);
	}
}
