package com.ialfaro.tokio.game;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/**
 * The mobs' attacks that shoot or throw something, for {@link Attacks}: the
 * game's own projectiles, numbers and sounds, from the spot the mob fires
 * from, toward where the player looks. The player owns what they fire, so
 * kills are theirs, and arrows and tridents can't be picked up.
 */
final class Shots {
	// How far the look reaches for what the player aims at: a ghast's range
	private static final double RANGE = 64;
	// A player's bow or crossbow is this precise; mobs miss on purpose
	private static final float PRECISE = 1.0F;
	// RangedBowAttackGoal shoots after 20 ticks of drawing: a full draw
	private static final float FULL_DRAW = 1.0F;
	// A player's throw of a splash potion goes this many degrees above the look
	private static final float THROW_UP = -20.0F;

	static final Map<EntityType<?>, Attack> ATTACKS = Map.ofEntries(
			// RangedAttackGoal's 40 for the middle head, 40 to 60 for each side head: about one skull in 15
			Map.entry(EntityTypes.WITHER, new WitherSkulls()),
			Map.entry(EntityTypes.GHAST, new Attack() {
				// GhastShootFireballGoal: the warning, the fireball 20 ticks after, then 40 to charge again
				@Override
				public int windup() {
					return 20;
				}

				@Override
				public int cooldown() {
					return 40;
				}

				@Override
				public LivingEntity start(ServerPlayer player) {
					player.level().levelEvent(null, 1015, player.blockPosition(), 0);
					return null;
				}

				@Override
				public void fire(ServerPlayer player, LivingEntity target) {
					ServerLevel level = player.level();
					Vec3 view = player.getViewVector(1.0F);
					Vec3 from = new Vec3(player.getX() + view.x * 4.0, player.getY(0.5) + 0.5, player.getZ() + view.z * 4.0);
					int power = Morph.instance(level, EntityTypes.GHAST) instanceof Ghast ghast ? ghast.getExplosionPower() : 1;
					level.levelEvent(null, 1016, player.blockPosition(), 0);
					LargeFireball fireball = new LargeFireball(level, player, toward(player, from), power);
					fireball.setPos(from);
					level.addFreshEntity(Attacks.own(fireball));
				}
			}),
			Map.entry(EntityTypes.BLAZE, new Attack() {
				// BlazeAttackGoal: three 6 ticks apart, then 100 (it also charges 60 before the first, left out)
				@Override
				public int shots() {
					return 3;
				}

				@Override
				public int gap() {
					return 6;
				}

				@Override
				public int cooldown() {
					return 100;
				}

				@Override
				public void fire(ServerPlayer player, LivingEntity target) {
					ServerLevel level = player.level();
					RandomSource random = player.getRandom();
					Vec3 at = lookedAt(player);
					double xd = at.x - player.getX();
					double yd = at.y - player.getY(0.5);
					double zd = at.z - player.getZ();
					// The blaze's spread grows with the distance
					double spread = Math.sqrt(Math.sqrt(player.position().distanceToSqr(at))) * 0.5;
					level.levelEvent(null, 1018, player.blockPosition(), 0);
					Vec3 direction = new Vec3(random.triangle(xd, 2.297 * spread), yd, random.triangle(zd, 2.297 * spread));
					SmallFireball fireball = new SmallFireball(level, player, direction.normalize());
					fireball.setPos(fireball.getX(), player.getY(0.5) + 0.5, fireball.getZ());
					level.addFreshEntity(Attacks.own(fireball));
				}
			}),
			// AbstractSkeleton draws its bow for 20 ticks, then waits 20 on Hard; the bogged waits 50
			Map.entry(EntityTypes.SKELETON, bow(null, 0, 20)),
			Map.entry(EntityTypes.STRAY, bow(MobEffects.SLOWNESS, 600, 20)),
			Map.entry(EntityTypes.BOGGED, bow(MobEffects.POISON, 100, 50)),
			Map.entry(EntityTypes.PARCHED, bow(MobEffects.WEAKNESS, 600, 20)),
			Map.entry(EntityTypes.ILLUSIONER, bow(null, 0, 20)),
			// RangedCrossbowAttackGoal: 25 ticks to load, then 20 to 40
			Map.entry(EntityTypes.PILLAGER, shot(40, Shots::crossbow)),
			Map.entry(EntityTypes.PIGLIN, shot(40, Shots::crossbow)),
			// DrownedTridentAttackGoal's interval
			Map.entry(EntityTypes.DROWNED, shot(40, (player, target) -> {
				ThrownTrident trident = new ThrownTrident(player.level(), player, new ItemStack(Items.TRIDENT));
				trident.pickup = AbstractArrow.Pickup.DISALLOWED;
				launch(player, trident, 1.6F, 0);
				sound(player, SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
			})),
			// The witch's RangedAttackGoal interval
			Map.entry(EntityTypes.WITCH, new Attack() {
				@Override
				public int cooldown() {
					return 60;
				}

				@Override
				public LivingEntity start(ServerPlayer player) {
					return Attacks.aimed(player, 16, false);
				}

				@Override
				public void fire(ServerPlayer player, LivingEntity target) {
					throwPotion(player, target);
				}
			}),
			// The snow golem's RangedAttackGoal interval
			Map.entry(EntityTypes.SNOW_GOLEM, shot(20, (player, target) -> {
				launch(player, new Snowball(player.level(), player, new ItemStack(Items.SNOWBALL)), 1.6F, 0);
				sound(player, SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
			})),
			// The llama's RangedAttackGoal interval
			Map.entry(EntityTypes.LLAMA, shot(40, Shots::spit)),
			Map.entry(EntityTypes.TRADER_LLAMA, shot(40, Shots::spit)),
			Map.entry(EntityTypes.SHULKER, new Attack() {
				// ShulkerAttackGoal: 20 to 110 between bullets
				@Override
				public int cooldown() {
					return 40;
				}

				@Override
				public LivingEntity start(ServerPlayer player) {
					return Attacks.aimed(player, 16, false);
				}

				@Override
				public boolean needsTarget() {
					return true;
				}

				@Override
				public void fire(ServerPlayer player, LivingEntity target) {
					// On the floor, as a shulker attached below: its bullet never starts along Y
					player.level().addFreshEntity(Attacks.own(new ShulkerBullet(player.level(), player, target, Direction.Axis.Y)));
					RandomSource random = player.getRandom();
					sound(player, SoundEvents.SHULKER_SHOOT, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
				}
			}),
			Map.entry(EntityTypes.BREEZE, new Attack() {
				// Shoot: it inhales for 15 ticks, then recovers 4 and cools down 10
				@Override
				public int windup() {
					return 15;
				}

				@Override
				public int cooldown() {
					return 14;
				}

				@Override
				public LivingEntity start(ServerPlayer player) {
					sound(player, SoundEvents.BREEZE_INHALE, 1.0F, 1.0F);
					return null;
				}

				@Override
				public void fire(ServerPlayer player, LivingEntity target) {
					// Its constructor wants a Breeze
					BreezeWindCharge charge = EntityTypes.BREEZE_WIND_CHARGE.create(player.level(), EntitySpawnReason.TRIGGERED);
					if (charge == null) return;
					charge.setOwner(player);
					// Breeze.getFiringYPosition
					Vec3 from = new Vec3(player.getX(), player.getY() + player.getBbHeight() / 2.0F + 0.3F, player.getZ());
					charge.setPos(from);
					Vec3 direction = toward(player, from);
					charge.shoot(direction.x, direction.y, direction.z, 0.7F, PRECISE);
					player.level().addFreshEntity(Attacks.own(charge));
					sound(player, SoundEvents.BREEZE_SHOOT, 1.5F, 1.0F);
				}
			}),
			// DragonStrafePlayerPhase fires once per strafe; 3 seconds stands in for the flight around
			Map.entry(EntityTypes.ENDER_DRAGON, shot(60, (player, target) -> {
				ServerLevel level = player.level();
				// Its head is 6.5 blocks ahead of its body, 1 block high; the fireball leaves 1 block behind it, half a block up
				float yRot = player.getYRot() * Mth.DEG_TO_RAD;
				Vec3 view = player.getViewVector(1.0F);
				Vec3 from = new Vec3(player.getX() - Mth.sin(yRot) * 6.5 - view.x, player.getY() + 1.0, player.getZ() + Mth.cos(yRot) * 6.5 - view.z);
				level.levelEvent(null, 1017, player.blockPosition(), 0);
				DragonFireball fireball = new DragonFireball(level, player, toward(player, from));
				fireball.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
				level.addFreshEntity(Attacks.own(fireball));
			})));

	private Shots() {
	}

	private static Attack shot(int cooldown, BiConsumer<ServerPlayer, LivingEntity> fire) {
		return new Attack() {
			@Override
			public int cooldown() {
				return cooldown;
			}

			@Override
			public void fire(ServerPlayer player, LivingEntity target) {
				fire.accept(player, target);
			}
		};
	}

	/** A skeleton's arrow (AbstractSkeleton.performRangedAttack), tipped as the stray's, the bogged's and the parched's are. */
	private static Attack bow(Holder<MobEffect> tip, int tipTicks, int cooldown) {
		return shot(cooldown, (player, target) -> {
			AbstractArrow arrow = ProjectileUtil.getMobArrow(player, new ItemStack(Items.ARROW), FULL_DRAW, new ItemStack(Items.BOW));
			if (tip != null && arrow instanceof Arrow tipped) tipped.addEffect(new MobEffectInstance(tip, tipTicks));
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			launch(player, arrow, 1.6F, 0);
			sound(player, SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
		});
	}

	/** A pillager's or piglin's bolt: CrossbowItem's arrow and power, shot along the look as it shoots without a target. */
	private static void crossbow(ServerPlayer player, LivingEntity target) {
		AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(player.level(), new ItemStack(Items.ARROW), player, new ItemStack(Items.CROSSBOW));
		arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		Vec3 view = player.getViewVector(1.0F);
		arrow.shoot(view.x, view.y, view.z, 1.6F, PRECISE);
		player.level().addFreshEntity(Attacks.own(arrow));
		sound(player, SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F);
	}

	/** Llama.spit, from beside its head. LlamaSpit's constructor wants a Llama. */
	private static void spit(ServerPlayer player, LivingEntity target) {
		LlamaSpit spit = EntityTypes.LLAMA_SPIT.create(player.level(), EntitySpawnReason.TRIGGERED);
		if (spit == null) return;
		spit.setOwner(player);
		float side = (player.getBbWidth() + 1.0F) * 0.5F;
		float yRot = player.yBodyRot * Mth.DEG_TO_RAD;
		spit.setPos(player.getX() - side * Mth.sin(yRot), player.getEyeY() - 0.1F, player.getZ() + side * Mth.cos(yRot));
		launch(player, spit, 1.5F, 0);
		RandomSource random = player.getRandom();
		sound(player, SoundEvents.LLAMA_SPIT, 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
	}

	/** Witch.performRangedAttack: the potion it picks for that target, Harming without one. */
	private static void throwPotion(ServerPlayer player, LivingEntity target) {
		Holder<Potion> potion = Potions.HARMING;
		double distance = 0;
		if (target != null) {
			Vec3 movement = target.getDeltaMovement();
			double xd = target.getX() + movement.x - player.getX();
			double zd = target.getZ() + movement.z - player.getZ();
			distance = Math.sqrt(xd * xd + zd * zd);
			if (target instanceof Raider) {
				potion = target.getHealth() <= 4.0F ? Potions.HEALING : Potions.REGENERATION;
			} else if (distance >= 8.0 && !target.hasEffect(MobEffects.SLOWNESS)) {
				potion = Potions.SLOWNESS;
			} else if (target.getHealth() >= 8.0F && !target.hasEffect(MobEffects.POISON)) {
				potion = Potions.POISON;
			} else if (distance <= 3.0 && !target.hasEffect(MobEffects.WEAKNESS) && player.getRandom().nextFloat() < 0.25F) {
				potion = Potions.WEAKNESS;
			}
		}
		ThrownSplashPotion thrown = new ThrownSplashPotion(player.level(), player, PotionContents.createItemStack(Items.SPLASH_POTION, potion));
		launch(player, thrown, target != null && distance <= 2.0 ? 0.45F : 0.75F, THROW_UP);
		sound(player, SoundEvents.WITCH_THROW, 1.0F, 0.8F + player.getRandom().nextFloat() * 0.4F);
	}

	/** Its three heads in turn (WitherBoss.performRangedAttack), each aimed at what the player looks at. */
	private static final class WitherSkulls implements Attack {
		// The head that fires next, per player
		private final Map<ServerPlayer, Integer> heads = new WeakHashMap<>();

		@Override
		public int cooldown() {
			return 15;
		}

		@Override
		public void fire(ServerPlayer player, LivingEntity target) {
			int head = heads.getOrDefault(player, 0);
			heads.put(player, (head + 1) % 3);
			ServerLevel level = player.level();
			// WitherBoss.getHeadX/Y/Z: the middle one on top, the others 1.3 blocks to either side, lower
			float scale = player.getScale();
			float angle = (player.yBodyRot + 180 * (head - 1)) * Mth.DEG_TO_RAD;
			Vec3 from = head == 0 ? new Vec3(player.getX(), player.getY() + 3.0F * scale, player.getZ())
					: new Vec3(player.getX() + Mth.cos(angle) * 1.3 * scale, player.getY() + 2.2F * scale, player.getZ() + Mth.sin(angle) * 1.3 * scale);
			level.levelEvent(null, 1024, player.blockPosition(), 0);
			WitherSkull skull = new WitherSkull(level, player, toward(player, from));
			skull.setPos(from);
			level.addFreshEntity(Attacks.own(skull));
		}
	}

	// From the player's eyes along the look, as a player throws, at the mob's speed and a player's precision
	private static void launch(ServerPlayer player, Projectile projectile, float velocity, float up) {
		projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), up, velocity, PRECISE);
		player.level().addFreshEntity(Attacks.own(projectile));
	}

	// From that spot to what the player looks at
	private static Vec3 toward(ServerPlayer player, Vec3 from) {
		return lookedAt(player).subtract(from).normalize();
	}

	/** What the player looks at: the mob they aim at, else the block, else the point {@link #RANGE} blocks on. */
	static Vec3 lookedAt(ServerPlayer player) {
		LivingEntity mob = Attacks.aimed(player, RANGE, false);
		if (mob != null) return mob.getBoundingBox().getCenter();
		Vec3 eyes = player.getEyePosition();
		Vec3 end = eyes.add(player.getViewVector(1.0F).scale(RANGE));
		// A miss ends at the end
		return player.level().clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
	}

	// Played for everyone, the player included, as the mob plays its own
	private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, player.getSoundSource(), volume, pitch);
	}
}
