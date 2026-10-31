package com.ialfaro.tokio.game;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A laser from far above: a thin red beam comes down and follows the player,
 * holds still and blinks while it charges, then comes down white-hot and
 * blasts a hole where it stands. A super laser brings the night, lights the
 * spot up, calls lightning and bores a deep tunnel. A super duper laser first
 * burns down through the ground over the player, so it reaches them
 * underground.
 */
public final class Laser extends Hazard {
	// Following the player, then holding still: the time to run
	private static final int AIM_TICKS = 40;
	private static final int LOCK_TICKS = 20;
	// The red beam comes down from the sky in this many ticks, the white one in these
	private static final int AIM_DROP_TICKS = 8;
	private static final int DROP_TICKS = 4;
	private static final int BLAST_TICKS = 5;
	private static final int SHOCKWAVE_TICKS = 10;
	private static final int FADE_TICKS = 6;
	// Smoke and embers rise from the hole this long after the beam
	private static final int SMOKE_TICKS = 40;
	// The red beam pulses half again wider and thinner than this
	private static final float AIM_WIDTH = 0.1F;
	private static final int RING_POINTS = 10;
	// A super laser's light, up along the beam, out of the blasts' way: light
	// blocks would stop them
	private static final int LIGHTS = 4;
	private static final int LIGHT_STEP = 4;
	// A super laser bores on down along the beam, this many blocks a tick, with
	// a blast every so often: blasts alone barely dent stone
	private static final int BORE_PER_TICK = 2;
	private static final int BORE_BLAST_EVERY = 8;
	private static final float BORE_POWER = 5;
	// A super duper laser first burns down through the ground over the spot,
	// this many blocks a tick
	private static final int OPEN_PER_TICK = 4;
	// Off vertical, so the beam leaves the player's feet beside their eyes
	// and they see it: one through the camera draws nothing
	private static final float TILT = (float) Math.toRadians(20);
	private static final int ABOVE_BUILD_LIMIT = 64;
	// The tick it fires at
	private static final int FIRE = AIM_TICKS + LOCK_TICKS;
	private static final DustParticleOptions RED = new DustParticleOptions(0xFF2020, 0.8F);
	private static final ColorParticleOption FLASH = ColorParticleOption.create(ParticleTypes.FLASH, 1F, 1F, 1F);
	// The super lasers under way, which keep it night
	private static final Set<Laser> POWERFUL = new HashSet<>();

	/** The name a laser's beams carry, so the mod draws them as beacon beams; others see the blocks. */
	public static final String BEAM = "tokio:laser";

	/**
	 * What a laser does: the white beam's width, the blasts drilling down (how
	 * many, how many blocks apart, how strong), the ring of blasts around the
	 * first one, a beam's width out, how far it bores on along the beam,
	 * whether it brings the night, light and lightning, and whether it first
	 * burns down through the ground over the spot.
	 */
	public record Kind(float width, int blasts, int step, float power, int ring, float ringPower, int depth, boolean powerful, boolean opens) {
	}

	public static final Kind LASER = new Kind(2.5F, 3, 2, 4, 0, 0, 0, false, false);
	public static final Kind SUPER = new Kind(5, 1, 0, 7, 6, 5, 64, true, false);
	public static final Kind SUPER_DUPER = new Kind(5, 1, 0, 7, 6, 5, 64, true, true);

	private final ServerPlayer player;
	private final Kind kind;
	private final Quaternionf tilt;
	// Up along the beam
	private final Vector3f axis;
	private final double top;
	private final List<BlockPos> lights = new ArrayList<>();
	private Vec3 target;
	private int age;
	// The ticks the blasts start, the beam fades and it ends at: later when a
	// super duper laser has ground to burn through
	private int firstBlast;
	private int fade;
	private int end;
	// How far the super duper laser has left to burn down through, and how far
	// the super laser has bored, in blocks along the beam
	private int open;
	private int bored;
	// Where the red beam's pulse is, in radians, and how far the super
	// laser's glow has turned
	private float pulse;
	private float spin;
	// The super laser's glow around the white beam
	private Hazard glow;

	private Laser(ServerPlayer player, Kind kind) {
		super(player.level());
		this.player = player;
		this.kind = kind;
		this.target = groundUnder(player.level(), player.position());
		this.top = Math.max(player.level().getMaxY(), target.y) + ABOVE_BUILD_LIMIT;
		double side = random.nextDouble() * Math.PI * 2;
		this.tilt = new Quaternionf().rotationAxis(TILT, (float) Math.cos(side), 0, (float) Math.sin(side));
		this.axis = tilt.transform(new Vector3f(0, 1, 0));
		schedule(FIRE + DROP_TICKS);
		setCustomName(Component.literal(BEAM));
		setPos(target);
		setPosRotInterpolationDuration(1);
		setBlockState(Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
		// A sliver at the top, which comes down
		setTransformation(beam(AIM_WIDTH, length(), 0));
		// Its far end is hundreds of blocks away
		setViewRange(4);
	}

	/** Aims a laser of that kind at the player. */
	public static Laser fire(ServerPlayer player, Kind kind) {
		Laser laser = new Laser(player, kind);
		player.level().addFreshEntity(laser);
		laser.sound(SoundEvents.GUARDIAN_ATTACK, 2, kind.powerful() ? 0.6F : 0.8F);
		if (kind.powerful()) {
			POWERFUL.add(laser);
			if (POWERFUL.size() == 1) Night.fall(player.level());
		}
		return laser;
	}

	/** Ends the super lasers before the world saves, so their lights go and the day comes back. */
	public static void stopAll() {
		List.copyOf(POWERFUL).forEach(Entity::discard);
	}

	// The blasts start then; the beam fades after them, or after the bore and a moment
	private void schedule(int firstBlast) {
		this.firstBlast = firstBlast;
		fade = firstBlast + Math.max(kind.blasts() * BLAST_TICKS, kind.depth() / BORE_PER_TICK + BLAST_TICKS);
		end = fade + FADE_TICKS + SMOKE_TICKS;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (age < FIRE) aim();
		if (age == AIM_TICKS) lockOn();
		if (age >= AIM_TICKS && age < FIRE) charge();
		if (age == FIRE) {
			// White, and only a sliver at the top...
			setBlockState(Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
			reshape(this, beam(kind.width(), length(), 0), 0);
			if (kind.powerful()) glow();
			if (kind.opens()) {
				open = cover();
				schedule(FIRE + DROP_TICKS + Mth.ceil(open / (float) OPEN_PER_TICK));
			}
		}
		if (age == FIRE + 1) {
			// ...that comes down to the ground, or onto the ground over the spot
			reshape(this, beam(kind.width(), open, 0), DROP_TICKS);
			if (glow != null) reshape(glow, beam(kind.width() * 1.6F, open, spin), DROP_TICKS);
			sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 5, 0.8F);
		}
		if (open > 0 && age > FIRE + DROP_TICKS) burnThrough();
		// Where the ground over the spot was, there's room for light now
		if (age == firstBlast && kind.opens()) lightUp();
		if (age >= firstBlast && age < fade) burn();
		if (age >= firstBlast && age < firstBlast + SHOCKWAVE_TICKS) shockwave(age - firstBlast);
		if (age == fade) {
			reshape(this, beam(0, -bored, 0), FADE_TICKS);
			if (glow != null) reshape(glow, beam(0, -bored, spin), FADE_TICKS);
			sound(SoundEvents.BEACON_DEACTIVATE, 2, 1);
		}
		if (age > fade + FADE_TICKS) smoke();
		if (age >= end) discard();
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		for (BlockPos pos : lights) {
			if (serverLevel().getBlockState(pos).is(Blocks.LIGHT)) serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		lights.clear();
		if (glow != null) glow.discard();
		if (POWERFUL.remove(this) && POWERFUL.isEmpty()) Night.lift();
	}

	// The red beam comes down, follows the player with a pulse that quickens as
	// it charges, then holds still and blinks, faster at the end. A red circle
	// closes in on where it will strike, and sparks run along the beam.
	private void aim() {
		if (age < AIM_TICKS) {
			follow();
		} else {
			int every = age < FIRE - 8 ? 3 : 1;
			DyeColor color = age / every % 2 == 0 ? DyeColor.WHITE : DyeColor.RED;
			setBlockState(Blocks.CONCRETE.pick(color).defaultBlockState());
		}
		pulse += 0.4F + 1.2F * age / FIRE;
		float width = AIM_WIDTH * (1 + 0.5F * (float) Math.sin(pulse));
		double gap = length() * Math.max(0, 1 - age / (double) AIM_DROP_TICKS);
		reshape(this, beam(width, gap, 0), 1);
		circle(Math.min(1, age / (double) AIM_TICKS));
		if (age >= AIM_DROP_TICKS) shimmer();
	}

	// Onto the ground under the player, until they leave the world or its dimension
	private void follow() {
		if (player.isRemoved() || player.level() != level()) return;
		target = groundUnder(serverLevel(), player.position());
		setPos(target);
	}

	// It holds still. A super laser lights the spot up from above.
	private void lockOn() {
		sound(SoundEvents.BEACON_POWER_SELECT, 2, 1.5F);
		if (kind.powerful()) lightUp();
	}

	// Light blocks up along the beam, where there's air
	private void lightUp() {
		for (int i = 1; i <= LIGHTS; i++) {
			BlockPos pos = BlockPos.containing(along(i * LIGHT_STEP));
			if (!serverLevel().getBlockState(pos).isAir()) continue;
			serverLevel().setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
			lights.add(pos);
		}
	}

	// Sparks stream in to the spot from all around. A super laser calls
	// lightning that only flashes and roars.
	private void charge() {
		double radius = kind.width() + 2;
		for (int i = 0; i < 6; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double x = Math.cos(angle);
			double z = Math.sin(angle);
			// With no count, the spread is the spark's way and the speed its speed
			serverLevel().sendParticles(ParticleTypes.END_ROD, true, false, target.x + x * radius, target.y + 0.3, target.z + z * radius, 0, -x, 0.1, -z, 0.15);
		}
		if (kind.powerful() && (age - AIM_TICKS) % 7 == 0) lightning();
	}

	private void lightning() {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(serverLevel(), EntitySpawnReason.TRIGGERED);
		if (bolt == null) return;
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = kind.width() + random.nextDouble() * 6;
		int x = Mth.floor(target.x + Math.cos(angle) * distance);
		int z = Mth.floor(target.z + Math.sin(angle) * distance);
		bolt.setPos(x + 0.5, serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
		bolt.setVisualOnly(true);
		serverLevel().addFreshEntity(bolt);
	}

	// The super laser's glow: a wider light blue beam turning around the white one
	private void glow() {
		glow = new Hazard(serverLevel()) {
		};
		glow.setCustomName(Component.literal(BEAM));
		glow.setPos(target);
		glow.setBlockState(Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
		glow.setTransformation(beam(kind.width() * 1.6F, length(), spin));
		glow.setViewRange(4);
		serverLevel().addFreshEntity(glow);
	}

	// The white beam flickers and the glow turns while the blasts drill down,
	// and a super laser bores on. The first blast has the ring around it, and
	// a sonic boom.
	private void burn() {
		if (kind.depth() > 0) bore();
		reshape(this, beam(kind.width() * (0.9F + 0.2F * random.nextFloat()), -bored, 0), 1);
		if (glow != null) {
			spin += 0.3F;
			reshape(glow, beam(kind.width() * 1.6F, -bored, spin), 1);
		}
		serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, target.x, target.y + 0.5, target.z, 6, kind.width() / 2, 0.5, kind.width() / 2, 0.2);
		int tick = age - firstBlast;
		if (tick % BLAST_TICKS != 0 || tick / BLAST_TICKS >= kind.blasts()) return;
		int blast = tick / BLAST_TICKS;
		Vec3 at = target.add(0, -blast * kind.step(), 0);
		blast(at, kind.power());
		serverLevel().sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y + 1, at.z, 40, 1, 1, 1, 0.3);
		serverLevel().sendParticles(FLASH, true, false, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		serverLevel().sendParticles(ParticleTypes.FIREWORK, true, false, at.x, at.y + 1, at.z, 12, 0.5, 0.5, 0.5, 0.3);
		if (blast > 0) return;
		for (int i = 0; i < kind.ring(); i++) {
			double angle = i * Math.PI * 2 / kind.ring();
			blast(target.add(Math.cos(angle) * kind.width(), 0, Math.sin(angle) * kind.width()), kind.ringPower());
		}
		serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, true, false, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		sound(SoundEvents.WARDEN_SONIC_BOOM, 3, 1);
	}

	// A tunnel a beam wide on down along the beam, through anything that
	// breaks, to the bottom of the world at most, with a blast every few blocks
	private void bore() {
		for (int i = 0; i < BORE_PER_TICK && bored < kind.depth() && target.y - axis.y() * (bored + 1) >= serverLevel().getMinY(); i++) {
			bored++;
			Vec3 front = along(-bored);
			if (breaks()) carve(front, kind.width() / 2);
			if (bored % BORE_BLAST_EVERY == 0) blast(front, BORE_POWER);
		}
		melt(along(-bored));
	}

	// The super duper laser burns down through the ground over the spot, and
	// its beam comes down with it
	private void burnThrough() {
		for (int i = 0; i < OPEN_PER_TICK && open > 0; i++) {
			open--;
			if (breaks()) carve(along(open), kind.width() / 2);
		}
		melt(along(open));
		reshape(this, beam(kind.width(), open, 0), 1);
		if (glow != null) reshape(glow, beam(kind.width() * 1.6F, open, spin), 1);
	}

	// How far up along the beam the ground over the spot goes, past its last
	// block: from where the beam is clear of the ground on, the sky is open
	private int cover() {
		int last = 0;
		for (int up = 1; up < length(); up++) {
			Vec3 at = along(up);
			BlockPos pos = BlockPos.containing(at);
			if (at.y > serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) + kind.width()) break;
			if (!serverLevel().getBlockState(pos).isAir()) last = up;
		}
		return last == 0 ? 0 : last + Mth.ceil(kind.width() / 2);
	}

	// Fire, lava and a hiss where the beam eats into the ground
	private void melt(Vec3 front) {
		serverLevel().sendParticles(ParticleTypes.LAVA, true, false, front.x, front.y, front.z, 4, 1, 1, 1, 0);
		serverLevel().sendParticles(ParticleTypes.FLAME, true, false, front.x, front.y, front.z, 10, 1, 1, 1, 0.05);
		serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, front.x, front.y, front.z, 6, 0.8, 0.8, 0.8, 0.03);
		if (age % 4 == 0) serverLevel().playSound(null, front.x, front.y, front.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2, 0.6F);
	}

	// Everything that breaks within the radius goes. What holds items drops them.
	private void carve(Vec3 center, float radius) {
		BlockPos from = BlockPos.containing(center.subtract(radius, radius, radius));
		BlockPos to = BlockPos.containing(center.add(radius, radius, radius));
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (pos.distToCenterSqr(center) > radius * radius) continue;
			BlockState state = serverLevel().getBlockState(pos);
			if (state.isAir() || state.getDestroySpeed(serverLevel(), pos) < 0) continue;
			BlockPos at = pos.immutable();
			if (serverLevel().getBlockEntity(at) != null) serverLevel().destroyBlock(at, true);
			else serverLevel().setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	// That many blocks up along the beam from the target, or down below it
	private Vec3 along(double distance) {
		return target.add(axis.x() * distance, axis.y() * distance, axis.z() * distance);
	}

	// A ring of dust racing out over the ground from the first blast
	private void shockwave(int tick) {
		double radius = kind.width() * 3 * (tick + 1) / SHOCKWAVE_TICKS;
		int points = 8 + (int) (radius * 4);
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2 / points;
			serverLevel().sendParticles(ParticleTypes.POOF, true, false, target.x + Math.cos(angle) * radius, target.y + 0.5, target.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	// Smoke and embers rising from the hole
	private void smoke() {
		double spread = kind.width() / 3;
		serverLevel().sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, false, target.x, target.y, target.z, 2, spread, 0.3, spread, 0.02);
		if (age % 3 == 0) serverLevel().sendParticles(ParticleTypes.LAVA, true, false, target.x, target.y, target.z, 2, spread, 0.3, spread, 0);
	}

	// The red circle, turning as it closes in to the white beam's edge: 0 is
	// all the way out, 1 in
	private void circle(double closed) {
		double radius = kind.width() / 2 + 2 * (1 - closed);
		for (int i = 0; i < RING_POINTS; i++) {
			double angle = age * 0.15 + i * Math.PI * 2 / RING_POINTS;
			serverLevel().sendParticles(RED, true, false, target.x + Math.cos(angle) * radius, target.y + 0.1, target.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	// Sparks along the beam's lower part
	private void shimmer() {
		for (int i = 0; i < 2; i++) {
			Vec3 at = along(1 + random.nextDouble() * 24);
			serverLevel().sendParticles(RED, true, false, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
		}
	}

	private void sound(SoundEvent sound, float volume, float pitch) {
		serverLevel().playSound(null, target.x, target.y, target.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	// Animated on the client over the ticks given
	private static void reshape(Display display, Transformation shape, int ticks) {
		display.setTransformationInterpolationDuration(ticks);
		display.setTransformation(shape);
		display.setTransformationInterpolationDelay(0);
	}

	// From the target up to the top, along the tilt
	private double length() {
		return (top - target.y) / Math.cos(TILT);
	}

	/**
	 * A beam, {@code width} wide, from {@code gap} blocks above the target up
	 * to the top, turned {@code spin} radians around its length.
	 */
	private Transformation beam(float width, double gap, float spin) {
		Quaternionf rotation = new Quaternionf(tilt).rotateY(spin);
		Vector3f corner = rotation.transform(new Vector3f(-width / 2, (float) gap, -width / 2));
		return new Transformation(corner, rotation, new Vector3f(width, (float) (length() - gap), width), null);
	}
}
