package com.ialfaro.tokio.game;

import com.ialfaro.tokio.mixin.EntityInvoker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turns players into mobs: the mob's size and look (through {@link #LOOK},
 * which the mixins and the client read), its stats, traits, sounds and attacks
 * ({@link Attacks}). Nothing
 * is saved: the stats are transient modifiers, the look a non-persistent
 * attachment and the effects last 3 seconds. Used on the server thread; one
 * world at a time runs in the integrated server.
 */
public final class Morph {
	/** The mob a player looks like, synced to clients with the mod. */
	public static final AttachmentType<EntityType<?>> LOOK = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("tokio", "morph"),
			builder -> builder.syncWith(ByteBufCodecs.registry(Registries.ENTITY_TYPE), AttachmentSyncPredicate.all()));

	private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("tokio", "morph");
	// The mob's own value replaces the player's, whenever the mob has the stat
	private static final List<Holder<Attribute>> STATS = List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE,
			Attributes.ATTACK_KNOCKBACK, Attributes.JUMP_STRENGTH, Attributes.STEP_HEIGHT, Attributes.GRAVITY, Attributes.SAFE_FALL_DISTANCE,
			Attributes.FALL_DAMAGE_MULTIPLIER, Attributes.BURNING_TIME, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, Attributes.ATTACK_DAMAGE, Attributes.MAX_HEALTH);
	// Every attribute a morph may touch, to undo
	private static final List<Holder<Attribute>> TOUCHED = new ArrayList<>(STATS);
	static {
		TOUCHED.addAll(List.of(Attributes.MOVEMENT_SPEED, Attributes.OXYGEN_BONUS, Attributes.BLOCK_INTERACTION_RANGE,
				Attributes.ENTITY_INTERACTION_RANGE, Attributes.CAMERA_DISTANCE));
	}
	private static final Set<EntityType<?>> FLYERS = Set.of(EntityTypes.ALLAY, EntityTypes.BAT, EntityTypes.BEE, EntityTypes.BLAZE,
			EntityTypes.ENDER_DRAGON, EntityTypes.GHAST, EntityTypes.HAPPY_GHAST, EntityTypes.PARROT, EntityTypes.PHANTOM, EntityTypes.VEX,
			EntityTypes.WITHER);
	// Creative flight's speed; these fly at half of it, and these at one and a half
	private static final float FLYING_SPEED = 0.05F;
	private static final float SLOW_FLYING_SPEED = FLYING_SPEED * 0.5F;
	private static final float FAST_FLYING_SPEED = FLYING_SPEED * 1.5F;
	private static final Set<EntityType<?>> SLOW_FLYERS = Set.of(EntityTypes.GHAST, EntityTypes.HAPPY_GHAST, EntityTypes.BLAZE);
	private static final Set<EntityType<?>> FAST_FLYERS = Set.of(EntityTypes.PHANTOM, EntityTypes.VEX, EntityTypes.ENDER_DRAGON);
	// A jump's push: creative flight lands a player who's on the ground, so a flyer takes off
	private static final double TAKE_OFF = 0.42;
	// How far around its body the dragon breaks what it flies into
	private static final double SMASH_REACH = 1;
	// The game rolls their health between 15 and 30 when they spawn; unspawned, they carry a placeholder
	private static final Set<EntityType<?>> ROLLED_HEALTH = Set.of(EntityTypes.HORSE, EntityTypes.DONKEY, EntityTypes.MULE, EntityTypes.LLAMA,
			EntityTypes.TRADER_LLAMA);
	private static final double ROLLED_HEALTH_VALUE = 22;
	private static final Set<EntityType<?>> NOT_MOBS = Set.of(EntityTypes.PLAYER, EntityTypes.MANNEQUIN, EntityTypes.ARMOR_STAND);
	// A player's walk, and the clamp around the mob's own
	private static final double WALK = 0.1;
	private static final double SLOWEST = 0.05;
	private static final double FASTEST = 0.15;
	// The oxygen_bonus attribute's maximum: the air almost never goes down
	private static final double BREATHE = 1024;
	private static final int EFFECT_TICKS = 60;
	private static final int RENEW_BELOW = 40;
	private static final int SLOW_FALL_AFTER_FLIGHT = 200;
	// A water animal out of water drowns once its air reaches this
	private static final int DROWNED = -20;
	// The biggest size the game searches room for when a mob grows (Entity.refreshDimensions)
	private static final float ROOM_SEARCH_MAX = 4;

	private static final Map<ServerPlayer, Morphed> MORPHED = new HashMap<>();
	// The forms that came while a timed one was on, in order: each waits for the one before to end
	private static final Map<ServerPlayer, Deque<Waiting>> WAITING = new HashMap<>();
	// One mob of each kind per side, made but never added to the world
	private static final Map<EntityType<?>, Mob> SERVER_INSTANCES = new HashMap<>();
	private static final Map<EntityType<?>, Mob> CLIENT_INSTANCES = new HashMap<>();
	private static int lastTick = -1;

	private Morph() {
	}

	private static final class Morphed {
		final EntityType<?> type;
		// The server tick it ends at, or -1 for the whole session; it moves on while the player is dead
		long endsAt;
		int ambientTime;
		int air;
		// Where the player was last tick, to tell a dragon flying down
		double lastY;

		Morphed(EntityType<?> type, long endsAt, int air) {
			this.type = type;
			this.endsAt = endsAt;
			this.air = air;
		}
	}

	private record Waiting(EntityType<?> type, OptionalInt seconds) {
	}

	/** Whether a player can turn into this: a living mob, not a player, mannequin or armor stand. */
	public static boolean canBecome(EntityType<?> type) {
		return DefaultAttributes.hasSupplier(type) && !NOT_MOBS.contains(type);
	}

	/** The mob a player looks like, or null. */
	public static EntityType<?> look(Entity entity) {
		return entity.getAttached(LOOK);
	}

	/** A mob of this kind on this side, for its stats, size and sounds, or null when it isn't a mob. */
	public static Mob instance(Level level, EntityType<?> type) {
		Map<EntityType<?>, Mob> instances = level.isClientSide() ? CLIENT_INSTANCES : SERVER_INSTANCES;
		Mob mob = instances.get(type);
		if (mob != null && mob.level() == level) return mob;
		Mob made = make(level, type);
		if (made != null) instances.put(type, made);
		return made;
	}

	/**
	 * A new mob of this kind, never added to the world, or null when it isn't a
	 * mob. Slimes and magma cubes are their biggest natural size, and the horses
	 * whose health the game rolls get 22.
	 */
	public static Mob make(Level level, EntityType<?> type) {
		if (!(type.create(level, EntitySpawnReason.LOAD) instanceof Mob mob)) return null;
		// AbstractCubeMob.setSpawnSize: up to 4, or 2 for a sulfur cube
		if (mob instanceof AbstractCubeMob cube) cube.setSize(cube instanceof SulfurCube ? 2 : 4, true);
		if (ROLLED_HEALTH.contains(type)) mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ROLLED_HEALTH_VALUE);
		return mob;
	}

	/** Forgets this side's mobs, as the client leaves a world. */
	public static void forgetClientInstances() {
		CLIENT_INSTANCES.clear();
	}

	/**
	 * Turns the player into the mob, for that many seconds or the whole session.
	 * While they're a mob for a set time, it waits its turn instead ({@link #waiting}),
	 * so a timed form always runs its course; one for the whole session gives way at
	 * once. False when it isn't a mob.
	 */
	public static boolean start(ServerPlayer player, EntityType<?> type, OptionalInt seconds) {
		if (!canBecome(type) || instance(player.level(), type) == null) return false;
		Morphed current = MORPHED.get(player);
		if (current != null && current.endsAt >= 0) {
			WAITING.computeIfAbsent(player, waiting -> new ArrayDeque<>()).add(new Waiting(type, seconds));
			return true;
		}
		become(player, type, ticks(seconds));
		return true;
	}

	private static long ticks(OptionalInt seconds) {
		return seconds.isPresent() ? seconds.getAsInt() * 20L : -1;
	}

	/** How many forms wait their turn for the player, the last one to come included. */
	public static int waiting(ServerPlayer player) {
		Deque<Waiting> waiting = WAITING.get(player);
		return waiting == null ? 0 : waiting.size();
	}

	// For that many ticks, or -1 for the whole session
	private static void become(ServerPlayer player, EntityType<?> type, long ticks) {
		Mob mob = instance(player.level(), type);
		if (mob == null) return;
		boolean flies = FLYERS.contains(type);
		Morphed old = MORPHED.get(player);
		float share = player.getHealth() / player.getMaxHealth();
		EntityDimensions before = player.getDimensions(player.getPose());
		Attacks.end(player);
		if (old != null) {
			removeEffects(player);
			// A flyer turning into another flyer keeps flying
			if (FLYERS.contains(old.type) && !flies) land(player);
		}
		setStats(player, mob);
		renewEffects(player, type);
		if (flies) fly(player, type);
		player.setAttached(LOOK, type);
		resize(player, before);
		player.setHealth(share * player.getMaxHealth());
		long endsAt = ticks >= 0 ? player.level().getServer().getTickCount() + ticks : -1;
		Morphed morphed = new Morphed(type, endsAt, old != null ? old.air : player.getAirSupply());
		morphed.lastY = player.getY();
		MORPHED.put(player, morphed);
	}

	// The next form in line, or null. One for the whole session gives way to whoever came after it,
	// as it does when they come while it's on, so it's skipped when another waits behind it
	private static Waiting next(ServerPlayer player) {
		Deque<Waiting> waiting = WAITING.get(player);
		if (waiting == null) return null;
		while (waiting.size() > 1 && waiting.peek().seconds().isEmpty()) waiting.poll();
		Waiting next = waiting.poll();
		if (waiting.isEmpty()) WAITING.remove(player);
		return next;
	}

	// Airborne at once, at the mob's speed
	private static void fly(ServerPlayer player, EntityType<?> type) {
		Abilities abilities = player.getAbilities();
		abilities.mayfly = true;
		abilities.setFlyingSpeed(SLOW_FLYERS.contains(type) ? SLOW_FLYING_SPEED : FAST_FLYERS.contains(type) ? FAST_FLYING_SPEED : FLYING_SPEED);
		// The push goes first: a client that flies on the ground lands at once
		if (!abilities.flying && player.onGround()) {
			Vec3 movement = player.getDeltaMovement();
			player.setDeltaMovement(movement.x, TAKE_OFF, movement.z);
			if (player.connection != null) player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
		abilities.flying = true;
		player.onUpdateAbilities();
	}

	/** A morph's flying speed is saved with the player: one a crash left behind goes back to creative's. */
	public static void joined(ServerPlayer player) {
		float speed = player.getAbilities().getFlyingSpeed();
		if (speed != SLOW_FLYING_SPEED && speed != FAST_FLYING_SPEED) return;
		player.getAbilities().setFlyingSpeed(FLYING_SPEED);
		player.onUpdateAbilities();
	}

	/** Turns the player back, if they're a mob, and drops the forms waiting their turn. */
	public static void end(ServerPlayer player) {
		WAITING.remove(player);
		Morphed morphed = MORPHED.remove(player);
		if (morphed == null) return;
		Attacks.end(player);
		float share = player.getHealth() / player.getMaxHealth();
		EntityDimensions before = player.getDimensions(player.getPose());
		for (Holder<Attribute> attribute : TOUCHED) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null) instance.removeModifier(MODIFIER);
		}
		removeEffects(player);
		if (FLYERS.contains(morphed.type)) land(player);
		player.removeAttached(LOOK);
		resize(player, before);
		player.setHealth(share * player.getMaxHealth());
	}

	/** After the players' ticks: the countdowns, the effects, and the mob's traits and idle sounds. */
	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		// Game tests may tick it more than once a tick
		if (now == lastTick) return;
		lastTick = now;
		for (Map.Entry<ServerPlayer, Morphed> entry : List.copyOf(MORPHED.entrySet())) {
			ServerPlayer player = entry.getKey();
			Morphed morphed = entry.getValue();
			// A dead player's body goes 20 ticks after they die, and they're still to respawn
			if (player.isRemoved() && player.getRemovalReason() != Entity.RemovalReason.KILLED) {
				MORPHED.remove(player);
				WAITING.remove(player);
			} else if (!player.isAlive()) {
				// Its time stands still while they're dead: it comes back with them (respawned)
				if (morphed.endsAt >= 0) morphed.endsAt++;
			} else if (morphed.endsAt >= 0 && now >= morphed.endsAt) {
				Waiting next = next(player);
				if (next != null) become(player, next.type(), ticks(next.seconds()));
				else end(player);
			} else if (player.isAlive()) {
				tick(player, morphed);
			}
		}
	}

	private static void tick(ServerPlayer player, Morphed morphed) {
		ServerLevel level = player.level();
		Mob mob = instance(level, morphed.type);
		if (mob == null) return;
		renewEffects(player, morphed.type);
		if (morphed.type == EntityTypes.ENDER_DRAGON) smash(player, morphed);
		if (holds(morphed.type, EntityTypeTags.BURN_IN_DAYLIGHT) && isSunBurnTick(player)) burnUndead(player);
		if (mob.isSensitiveToWater() && player.isInWaterOrRain()) player.hurtServer(level, player.damageSources().drown(), 1);
		if (mob instanceof WaterAnimal) handleAirSupply(player, morphed);
		RandomSource random = player.getRandom();
		if (random.nextInt(1000) < morphed.ambientTime++) {
			morphed.ambientTime = -mob.getAmbientSoundInterval();
			mob.setPos(player.position());
			mob.playAmbientSound();
		}
		Attacks.tick(player, morphed.type, level.getServer().getTickCount());
	}

	/** Its steps, played by the mob where the player stands: the server sends them to everyone. */
	public static void playStepSound(Entity player, EntityType<?> type, BlockPos pos, BlockState state) {
		Mob mob = instance(player.level(), type);
		if (mob == null) return;
		mob.setPos(player.position());
		((EntityInvoker) mob).tokio$playStepSound(pos, state);
	}

	/**
	 * Puts the morph back on a player who respawned, with the time it had left as
	 * they died (or left the End), and the forms waiting their turn after it.
	 */
	public static void respawned(ServerPlayer old, ServerPlayer player, boolean alive) {
		Deque<Waiting> waiting = WAITING.remove(old);
		Morphed morphed = MORPHED.remove(old);
		if (morphed == null) return;
		// Leaving the End copies the effects and the attachments; the morph starts over on the new player
		removeEffects(player);
		player.removeAttached(LOOK);
		player.removeAttached(Attacks.WINDUP);
		player.refreshDimensions();
		if (alive) player.setHealth(old.getHealth() / old.getMaxHealth() * player.getMaxHealth());
		long now = player.level().getServer().getTickCount();
		become(player, morphed.type, morphed.endsAt < 0 ? -1 : Math.max(1, morphed.endsAt - now));
		if (waiting != null) WAITING.put(player, waiting);
	}

	/** Turns everyone back before the world saves, and forgets the mobs. */
	public static void stopAll(MinecraftServer server) {
		for (ServerPlayer player : List.copyOf(MORPHED.keySet())) end(player);
		MORPHED.clear();
		WAITING.clear();
		Attacks.stopAll();
		SERVER_INSTANCES.clear();
		lastTick = -1;
	}

	private static void setStats(ServerPlayer player, Mob mob) {
		for (Holder<Attribute> attribute : TOUCHED) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null) instance.removeModifier(MODIFIER);
		}
		for (Holder<Attribute> attribute : STATS) {
			if (mob.getAttributes().hasAttribute(attribute)) set(player, attribute, mob.getAttributeValue(attribute));
		}
		EntityType<?> type = mob.getType();
		if (holds(type, EntityTypeTags.FALL_DAMAGE_IMMUNE)) set(player, Attributes.FALL_DAMAGE_MULTIPLIER, 0);
		if (holds(type, EntityTypeTags.AQUATIC)) {
			set(player, Attributes.MOVEMENT_SPEED, WALK / 2);
		} else if (!FLYERS.contains(type) && mob.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)) {
			// The push a mob gets each tick for its speed attribute
			double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
			set(player, Attributes.MOVEMENT_SPEED, Math.clamp(speed * speed / 0.98, SLOWEST, FASTEST));
		}
		if (mob.canBreatheUnderwater()) add(player, Attributes.OXYGEN_BONUS, BREATHE, AttributeModifier.Operation.ADD_VALUE);
		double taller = mob.getBbHeight() / player.getType().getHeight() - 1;
		if (taller > 0) {
			add(player, Attributes.BLOCK_INTERACTION_RANGE, taller, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			add(player, Attributes.ENTITY_INTERACTION_RANGE, taller, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			add(player, Attributes.CAMERA_DISTANCE, taller, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	// The player's attribute becomes the value, whatever their base; armor and weapons still add on top
	private static void set(ServerPlayer player, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) add(player, attribute, value - instance.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
	}

	private static void add(ServerPlayer player, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, amount, operation));
	}

	// Fire Resistance for fireproof mobs, Slow Falling for chickens, hidden and short, renewed while they last
	private static void renewEffects(ServerPlayer player, EntityType<?> type) {
		if (type.fireImmune()) renew(player, MobEffects.FIRE_RESISTANCE);
		if (type == EntityTypes.CHICKEN) renew(player, MobEffects.SLOW_FALLING);
	}

	private static void renew(ServerPlayer player, Holder<MobEffect> effect) {
		MobEffectInstance current = player.getEffect(effect);
		// A potion's shows its icon; it's left alone
		if (current != null && (current.showIcon() || current.getDuration() > RENEW_BELOW)) return;
		player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, 0, false, false, false));
	}

	private static void removeEffects(ServerPlayer player) {
		for (Holder<MobEffect> effect : List.of(MobEffects.FIRE_RESISTANCE, MobEffects.SLOW_FALLING)) {
			MobEffectInstance current = player.getEffect(effect);
			if (current != null && !current.showIcon()) player.removeEffect(effect);
		}
	}

	// Back to the game mode's flight, at creative's speed; one in the air floats down
	private static void land(ServerPlayer player) {
		boolean flying = player.getAbilities().flying;
		player.gameMode().updatePlayerAbilities(player.getAbilities());
		player.getAbilities().setFlyingSpeed(FLYING_SPEED);
		player.onUpdateAbilities();
		if (flying && !player.getAbilities().flying) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_AFTER_FLIGHT));
	}

	// The game never nudges a player who grows out of a gap, so this does, and tells their client.
	// Its search for room takes minutes past 4×4 by terrain (it stops there for mobs), so a
	// bigger mob rises until it fits instead, at most its own height.
	private static void resize(ServerPlayer player, EntityDimensions before) {
		player.refreshDimensions();
		EntityDimensions after = player.getDimensions(player.getPose());
		if (after.width() <= before.width() && after.height() <= before.height()) return;
		if (after.width() <= ROOM_SEARCH_MAX && after.height() <= ROOM_SEARCH_MAX) {
			if (player.fudgePositionAfterSizeChange(before)) place(player, player.getY());
			return;
		}
		for (int up = 0; up <= after.height(); up++) {
			if (!player.level().noCollision(player, player.getBoundingBox().move(0, up, 0))) continue;
			if (up > 0) place(player, player.getY() + up);
			return;
		}
	}

	// Puts the player at that height, on their client too
	private static void place(ServerPlayer player, double y) {
		if (player.connection != null) player.teleportTo(player.getX(), y, player.getZ());
		else player.setPos(player.getX(), y, player.getZ());
	}

	// EnderDragon.checkWalls, around the player's body: what the dragon flies into breaks, as
	// the real one's does, but not what it can't break, nothing while mobGriefing is off, and
	// not the ground under it unless it flies down, or it would sink through the world
	private static void smash(ServerPlayer player, Morphed morphed) {
		boolean down = player.getAbilities().flying && player.getY() < morphed.lastY;
		morphed.lastY = player.getY();
		ServerLevel level = player.level();
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return;
		AABB reach = player.getBoundingBox().inflate(SMASH_REACH);
		int x0 = Mth.floor(reach.minX);
		// The block its feet are in, even on a slab, but not the one they stand on
		int y0 = down ? Mth.floor(reach.minY) : Mth.floor(player.getY() + 0.5);
		int z0 = Mth.floor(reach.minZ);
		int x1 = Mth.floor(reach.maxX);
		int y1 = Mth.floor(reach.maxY);
		int z1 = Mth.floor(reach.maxZ);
		boolean broke = false;
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(BlockTags.DRAGON_TRANSPARENT) || state.is(BlockTags.DRAGON_IMMUNE)) continue;
			broke = level.removeBlock(pos, false) || broke;
		}
		RandomSource random = player.getRandom();
		if (broke) level.levelEvent(LevelEvent.PARTICLES_DRAGON_BLOCK_BREAK, new BlockPos(x0 + random.nextInt(x1 - x0 + 1), y0 + random.nextInt(y1 - y0 + 1), z0 + random.nextInt(z1 - z0 + 1)), 0);
	}

	// Mob.isSunBurnTick, for the player
	private static boolean isSunBurnTick(ServerPlayer player) {
		Level level = player.level();
		if (!level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position())) return false;
		float brightness = player.getLightLevelDependentMagicValue();
		BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		boolean shielded = player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow;
		return brightness > 0.5F && player.getRandom().nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F && !shielded && level.canSeeSky(eyes);
	}

	// Mob.burnUndead: a helmet takes the sun instead
	private static void burnUndead(ServerPlayer player) {
		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		if (helmet.isEmpty()) {
			player.igniteForSeconds(8.0F);
		} else if (helmet.isDamageableItem()) {
			helmet.hurtAndBreak(player.getRandom().nextInt(2), player, EquipmentSlot.HEAD);
		}
	}

	// WaterAnimal.handleAirSupply: out of water the air runs out, then it hurts. The
	// player's own tick refills it on land, so the count is kept here.
	private static void handleAirSupply(ServerPlayer player, Morphed morphed) {
		if (player.isInWater()) {
			morphed.air = player.getMaxAirSupply();
			return;
		}
		morphed.air--;
		if (morphed.air <= DROWNED) {
			morphed.air = 0;
			player.hurtServer(player.level(), player.damageSources().drown(), 2.0F);
		}
		player.setAirSupply(morphed.air);
	}

	private static boolean holds(EntityType<?> type, TagKey<EntityType<?>> tag) {
		return type.builtInRegistryHolder().is(tag);
	}
}
