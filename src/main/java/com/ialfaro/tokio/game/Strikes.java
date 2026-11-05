package com.ialfaro.tokio.game;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mobs' attacks that hit without a projectile, for {@link Attacks}: the
 * Warden's sonic boom, the evoker's fangs, the guardians' beam and the
 * creeper's blast, with the game's own numbers. None of them hurts a player.
 */
final class Strikes {
	static final Map<EntityType<?>, Attack> ATTACKS = Map.of(
			EntityTypes.WARDEN, new SonicBoom(),
			EntityTypes.EVOKER, new Fangs(),
			EntityTypes.GUARDIAN, new Beam(EntityTypes.GUARDIAN, 80),
			EntityTypes.ELDER_GUARDIAN, new Beam(EntityTypes.ELDER_GUARDIAN, 60),
			EntityTypes.CREEPER, new Blast());

	private Strikes() {
	}

	/** A mob of this kind where the player is, to play its sounds as the mob would, to everyone: a player's own skip them. */
	static Mob mob(ServerPlayer player, EntityType<?> type) {
		Mob mob = Morph.instance(player.level(), type);
		mob.setPos(player.position());
		return mob;
	}

	/**
	 * The Warden's sonic boom (SonicBoom): it charges for 34 ticks, then hits the
	 * first living thing along the look within 15 blocks, through walls, for 10
	 * and a push. It plays for 60 ticks and cools down for 40, so the next one
	 * comes 100 ticks after the start.
	 */
	private static final class SonicBoom implements Attack {
		private static final double RANGE = 15;
		private static final float DAMAGE = 10;

		@Override
		public int windup() {
			return 34;
		}

		@Override
		public int cooldown() {
			return 66;
		}

		@Override
		public LivingEntity start(ServerPlayer player) {
			mob(player, EntityTypes.WARDEN).playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
			return null;
		}

		@Override
		public void fire(ServerPlayer player, LivingEntity aimed) {
			ServerLevel level = player.level();
			Mob warden = mob(player, EntityTypes.WARDEN);
			LivingEntity target = Attacks.aimed(player, RANGE, true);
			Vec3 source = player.position().add(warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, player.getYRot()));
			Vec3 end = target != null ? target.getEyePosition() : player.getEyePosition().add(player.getViewVector(1.0F).scale(RANGE));
			Vec3 delta = end.subtract(source);
			Vec3 direction = delta.normalize();
			for (int i = 1; i <= Mth.floor(delta.length()); i++) {
				Vec3 at = source.add(direction.scale(i));
				level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			warden.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
			if (target != null && target.hurtServer(level, level.damageSources().sonicBoom(player), DAMAGE)) {
				double kept = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				target.push(direction.x * 2.5 * kept, direction.y * 0.5 * kept, direction.z * 2.5 * kept);
			}
		}
	}

	/**
	 * The evoker's fangs (Evoker.EvokerAttackSpellGoal): 20 ticks of warmup, then
	 * 16 fangs in a line along the player's horizontal look, 1.25 blocks apart,
	 * each a tick after the last. It casts again 100 ticks after the start. No
	 * vexes: they'd attack their summoner.
	 */
	private static final class Fangs implements Attack {
		private static final int FANGS = 16;
		private static final double SPACING = 1.25;

		@Override
		public int windup() {
			return 20;
		}

		@Override
		public int cooldown() {
			return 80;
		}

		@Override
		public LivingEntity start(ServerPlayer player) {
			mob(player, EntityTypes.EVOKER).playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.0F);
			return null;
		}

		@Override
		public void fire(ServerPlayer player, LivingEntity target) {
			float angle = (player.getYRot() + 90.0F) * Mth.DEG_TO_RAD;
			for (int i = 0; i < FANGS; i++) {
				double reach = SPACING * (i + 1);
				fang(player, player.getX() + Mth.cos(angle) * reach, player.getZ() + Mth.sin(angle) * reach, angle, i);
			}
			mob(player, EntityTypes.EVOKER).playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
		}

		// The goal's createSpellEntity, its target at the player's height: on the ground from a block over their feet to one under
		private static void fang(ServerPlayer player, double x, double z, float angle, int delay) {
			Level level = player.level();
			double minY = player.getY();
			BlockPos pos = BlockPos.containing(x, minY + 1.0, z);
			do {
				BlockPos below = pos.below();
				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					double top = 0.0;
					if (!level.isEmptyBlock(pos)) {
						BlockState state = level.getBlockState(pos);
						VoxelShape shape = state.getCollisionShape(level, pos);
						if (!shape.isEmpty()) top = shape.max(Direction.Axis.Y);
					}
					level.addFreshEntity(Attacks.own(new EvokerFangs(level, x, pos.getY() + top, z, angle, delay, player)));
					level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, pos.getY() + top, z), GameEvent.Context.of(player));
					return;
				}
				pos = pos.below();
			} while (pos.getY() >= Mth.floor(minY) - 1);
		}
	}

	/**
	 * The guardians' beam (Guardian.GuardianAttackGoal), at the mob in the
	 * crosshair within 16 blocks: it charges for 80 ticks (an elder's 60) while
	 * the target lives, stays in range and in sight, then hits with magic (1,
	 * +2 on Hard, +2 for an elder) and the mob's own hit.
	 */
	private record Beam(EntityType<?> type, int windup) implements Attack {
		private static final double RANGE = 16;

		@Override
		public int cooldown() {
			return 20;
		}

		@Override
		public boolean needsTarget() {
			return true;
		}

		@Override
		public LivingEntity start(ServerPlayer player) {
			LivingEntity target = Attacks.aimed(player, RANGE, false);
			if (target != null) mob(player, type).playSound(SoundEvents.GUARDIAN_ATTACK, 1.0F, 1.0F);
			return target;
		}

		@Override
		public boolean holds(ServerPlayer player, LivingEntity target) {
			return target.isAlive() && player.closerThan(target, RANGE) && player.hasLineOfSight(target);
		}

		@Override
		public void fire(ServerPlayer player, LivingEntity target) {
			ServerLevel level = player.level();
			float magic = 1.0F;
			if (level.getDifficulty() == Difficulty.HARD) magic += 2.0F;
			if (type == EntityTypes.ELDER_GUARDIAN) magic += 2.0F;
			target.hurtServer(level, player.damageSources().indirectMagic(player, player), magic);
			// Mob.doHurtTarget's hit, with the attack damage the morph gave the player
			target.hurtServer(level, player.damageSources().mobAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
		}
	}

	/**
	 * The creeper's blast (Creeper.explodeCreeper): 30 ticks of fuse, then a
	 * radius 3 blast at the player that breaks blocks as mobGriefing allows.
	 * Players take neither its damage nor its push, so the player lives on.
	 */
	private static final class Blast implements Attack {
		private static final float RADIUS = 3;
		private static final ExplosionDamageCalculator SPARES_PLAYERS = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				return !(entity instanceof Player);
			}

			@Override
			public float getKnockbackMultiplier(Entity entity) {
				return entity instanceof Player ? 0.0F : 1.0F;
			}
		};

		@Override
		public int windup() {
			return 30;
		}

		@Override
		public int cooldown() {
			return 30;
		}

		@Override
		public LivingEntity start(ServerPlayer player) {
			mob(player, EntityTypes.CREEPER).playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
			player.gameEvent(GameEvent.PRIME_FUSE);
			return null;
		}

		@Override
		public void fire(ServerPlayer player, LivingEntity target) {
			player.level().explode(player, null, SPARES_PLAYERS, player.getX(), player.getY(), player.getZ(), RADIUS, false, Level.ExplosionInteraction.MOB);
		}
	}
}
