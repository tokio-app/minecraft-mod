package com.ialfaro.tokio.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * What a morphed player's melee hit does on top of its damage, as the mob's
 * own bite or punch does (each mob's doHurtTarget): its effects, its push and
 * its sound. Only on what isn't a player.
 */
public final class Hits {
	private Hits() {
	}

	/** After anything takes damage (Fabric's {@code AFTER_DAMAGE}). */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (blocked || damage <= 0 || entity instanceof Player || !source.is(DamageTypes.PLAYER_ATTACK)) return;
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) return;
		EntityType<?> type = Morph.look(player);
		if (type == null) return;
		ServerLevel level = player.level();
		Difficulty difficulty = level.getDifficulty();
		// Zombie.doHurtTarget, for its whole family: a burning zombie's bare hand may set what it hits on fire
		if (Morph.instance(level, type) instanceof Zombie && player.getMainHandItem().isEmpty() && player.isOnFire()) {
			float regional = level.getCurrentDifficultyAt(player.blockPosition()).getEffectiveDifficulty();
			if (player.getRandom().nextFloat() < regional * 0.3F) entity.igniteForSeconds(2 * (int) regional);
		}
		if (type == EntityTypes.WITHER_SKELETON) {
			entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 200), player);
		} else if (type == EntityTypes.HUSK && player.getMainHandItem().isEmpty()) {
			float regional = level.getCurrentDifficultyAt(player.blockPosition()).getEffectiveDifficulty();
			entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int) regional), player);
		} else if (type == EntityTypes.CAVE_SPIDER) {
			poison(entity, player, difficulty == Difficulty.NORMAL ? 7 : difficulty == Difficulty.HARD ? 15 : 0);
		} else if (type == EntityTypes.BEE) {
			// The stinger stays in what it stung; unlike a bee, the player keeps theirs and lives
			entity.setStingerCount(entity.getStingerCount() + 1);
			poison(entity, player, difficulty == Difficulty.NORMAL ? 10 : difficulty == Difficulty.HARD ? 18 : 0);
			Strikes.mob(player, type).playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
		} else if (type == EntityTypes.IRON_GOLEM) {
			double scale = Math.max(0.0, 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, 0.4F * scale, 0.0));
			Strikes.mob(player, type).playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F);
		} else if (type == EntityTypes.HOGLIN || type == EntityTypes.ZOGLIN) {
			HoglinBase.throwTarget(player, entity);
			Strikes.mob(player, type).makeSound(type == EntityTypes.HOGLIN ? SoundEvents.HOGLIN_ATTACK : SoundEvents.ZOGLIN_ATTACK);
		} else if (type == EntityTypes.RAVAGER) {
			Strikes.mob(player, type).playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 1.0F);
		} else if (type == EntityTypes.WARDEN) {
			Mob warden = Strikes.mob(player, type);
			warden.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 10.0F, warden.getVoicePitch());
		} else if (type == EntityTypes.CREAKING) {
			Strikes.mob(player, type).makeSound(SoundEvents.CREAKING_ATTACK);
		} else if (type == EntityTypes.AXOLOTL || type == EntityTypes.DOLPHIN || type == EntityTypes.PANDA) {
			// Their playAttackSound, which Mob.doHurtTarget plays
			Strikes.mob(player, type).playSound(type == EntityTypes.AXOLOTL ? SoundEvents.AXOLOTL_ATTACK
					: type == EntityTypes.DOLPHIN ? SoundEvents.DOLPHIN_ATTACK : SoundEvents.PANDA_BITE, 1.0F, 1.0F);
		}
	}

	private static void poison(LivingEntity entity, ServerPlayer player, int seconds) {
		if (seconds > 0) entity.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 0), player);
	}
}
