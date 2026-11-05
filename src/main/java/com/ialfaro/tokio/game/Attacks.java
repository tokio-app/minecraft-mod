package com.ialfaro.tokio.game;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A morphed player's mob attacks, on the attack key (R by default): the mob's
 * own, at its own pace, where the player looks. {@link Shots} has the
 * projectiles and {@link Strikes} the rest; {@link Hits} adds what the mob's
 * bite or punch does to the player's melee hits. What an attack makes is
 * Tokio's ({@link #own}), so it never hurts another player, nor the player.
 * Nothing is saved. Used on the server thread.
 */
public final class Attacks {
	/**
	 * While a morphed player's attack winds up or fires a burst, the id of what
	 * it's aimed at, or 0; removed when it's done. Synced to clients with the
	 * mod, which draw the mob charging. Never saved.
	 */
	public static final AttachmentType<Integer> WINDUP = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("tokio", "windup"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	// How far around a mob the look still picks it
	private static final float AIM_MARGIN = 0.5F;

	private static final Map<EntityType<?>, Attack> ATTACKS = new HashMap<>();
	static {
		ATTACKS.putAll(Shots.ATTACKS);
		ATTACKS.putAll(Strikes.ATTACKS);
	}

	// Weak, so a player who leaves isn't kept
	private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

	private Attacks() {
	}

	private static final class State {
		boolean held;
		// The server tick the next attack can start at
		long readyAt;
		// The attack under way, what it's aimed at, its next shot's tick and the shots it has left
		Attack attack;
		LivingEntity target;
		long nextShotAt;
		int shotsLeft;
	}

	/** The mob's attack, or null when it has none. */
	public static Attack of(EntityType<?> type) {
		return ATTACKS.get(type);
	}

	/** The player's attack key went down or up ({@link AttackPayload}). */
	public static void held(ServerPlayer player, boolean held) {
		State state = STATES.get(player);
		if (state == null) {
			if (!held) return;
			state = new State();
			STATES.put(player, state);
		}
		state.held = held;
	}

	/** After a morphed player's tick ({@code Morph.tick}): starts, winds up and fires their mob's attack. */
	static void tick(ServerPlayer player, EntityType<?> type, long now) {
		State state = STATES.get(player);
		if (state == null) return;
		if (state.attack == null) {
			Attack attack = ATTACKS.get(type);
			if (!state.held || attack == null || now < state.readyAt || player.isSpectator()) return;
			LivingEntity target = attack.start(player);
			if (attack.needsTarget() && target == null) return;
			state.attack = attack;
			state.target = target;
			state.shotsLeft = attack.shots();
			state.nextShotAt = now + attack.windup();
			if (attack.windup() > 0 || attack.shots() > 1) player.setAttached(WINDUP, target != null ? target.getId() : 0);
		} else if (now < state.nextShotAt && state.shotsLeft == state.attack.shots() && !state.attack.holds(player, state.target)) {
			stop(player, state);
			return;
		}
		if (now < state.nextShotAt) return;
		state.attack.fire(player, state.target);
		if (--state.shotsLeft > 0) {
			state.nextShotAt = now + state.attack.gap();
			return;
		}
		state.readyAt = now + state.attack.cooldown();
		stop(player, state);
	}

	private static void stop(ServerPlayer player, State state) {
		state.attack = null;
		state.target = null;
		player.removeAttached(WINDUP);
	}

	/** Calls off the player's attack and its cooldown, as their morph ends or changes. The key stays as it is. */
	static void end(ServerPlayer player) {
		State state = STATES.get(player);
		if (state == null) return;
		state.readyAt = 0;
		stop(player, state);
	}

	/** Forgets everyone's, as the world closes. */
	static void stopAll() {
		STATES.clear();
	}

	/**
	 * Marks what an attack made as Tokio's, as Tokio's commands mark theirs, so
	 * it spares other players ({@link Host}). Call it before adding the entity.
	 */
	public static <T extends Entity> T own(T entity) {
		entity.addTag(Spawned.TAG);
		return entity;
	}

	/**
	 * The living thing the player looks at within range, never a player, a
	 * mannequin or an armor stand, or null. Blocks stop the look unless it goes
	 * through walls, as the Warden's sonic boom does.
	 */
	public static LivingEntity aimed(ServerPlayer player, double range, boolean throughWalls) {
		Vec3 eyes = player.getEyePosition();
		Vec3 end = eyes.add(player.getViewVector(1.0F).scale(range));
		if (!throughWalls) {
			HitResult block = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
		}
		AABB around = player.getBoundingBox().expandTowards(end.subtract(eyes)).inflate(AIM_MARGIN + 1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eyes, end, around,
				entity -> entity instanceof LivingEntity living && living.isAlive() && !(entity instanceof Avatar) && !(entity instanceof ArmorStand), AIM_MARGIN);
		return hit != null ? (LivingEntity) hit.getEntity() : null;
	}
}
