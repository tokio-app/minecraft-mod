package com.ialfaro.tokio.client;

import com.ialfaro.tokio.Tokio;
import com.ialfaro.tokio.game.Morph;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Draws morphed players as their mob, on clients with the mod: once the world's
 * entities are extracted, each morphed player's state is swapped for one of a
 * mob that stands and moves where they do. The local player's own state for
 * the first-person hands and the inventory stay the player's.
 */
public final class MorphLooks implements ClientModInitializer {
	// One mob per morphed player, holding that player's pose and items, by player id
	private static final Map<Integer, Mob> COPIES = new HashMap<>();
	// The look each player's size was last refreshed for
	private static final Map<Integer, EntityType<?>> SIZED = new HashMap<>();
	// The mob each player was last drawn as
	private static final Map<Integer, EntityType<?>> DRAWN = new HashMap<>();
	// Kinds that failed to draw, logged once and drawn as players from then on
	private static final Set<EntityType<?>> FAILED = new HashSet<>();
	// Kinds whose tick failed, logged once and kept still from then on
	private static final Set<EntityType<?>> STILL = new HashSet<>();
	private static ClientLevel lastLevel;

	@Override
	public void onInitializeClient() {
		LevelExtractionEvents.END_EXTRACTION.register(MorphLooks::swap);
		ClientTickEvents.END_CLIENT_TICK.register(MorphLooks::tick);
	}

	/** The mob this player was last drawn as, or null. For the client game test. */
	public static EntityType<?> drawnAs(Entity player) {
		return DRAWN.get(player.getId());
	}

	/** The kinds that failed to draw or to animate. For the client game test. */
	public static Set<EntityType<?>> failed() {
		Set<EntityType<?>> failed = new HashSet<>(FAILED);
		failed.addAll(STILL);
		return failed;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			COPIES.clear();
			SIZED.clear();
			DRAWN.clear();
			Morph.forgetClientInstances();
		}
		if (level == null) return;
		for (Player player : level.players()) {
			EntityType<?> look = Morph.look(player);
			// The player moves here too, so their size must change here as well
			if (SIZED.get(player.getId()) != look) {
				player.refreshDimensions();
				SIZED.put(player.getId(), look);
			}
			if (look == null) DRAWN.remove(player.getId());
			if (look != null && COPIES.get(player.getId()) instanceof Mob mob && mob.getType() == look) tickCopy(player, mob);
		}
		COPIES.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player player) || Morph.look(player) == null);
	}

	// The mob's own animation runs in its tick: wings flap, the dragon growls, the Warden's heart beats
	private static void tickCopy(Player player, Mob mob) {
		if (STILL.contains(mob.getType())) return;
		mob.setPos(player.position());
		// The dragon turns by the flight history its tick records, which faces away from its yaw
		mob.setYRot(mob instanceof EnderDragon ? player.getYRot() + 180 : player.getYRot());
		mob.setXRot(player.getXRot());
		// Its wings beat by how it moves
		mob.setDeltaMovement(player.getDeltaMovement());
		mob.setOnGround(player.onGround());
		try {
			mob.tick();
		} catch (RuntimeException e) {
			STILL.add(mob.getType());
			Tokio.LOGGER.error("Couldn't animate a player as {}; they keep its still pose", mob.getType(), e);
		}
	}

	private static void swap(LevelExtractionContext context) {
		ClientLevel level = context.level();
		List<EntityRenderState> states = context.levelState().entityRenderStates;
		for (int i = 0; i < states.size(); i++) {
			if (!(states.get(i) instanceof AvatarRenderState avatar) || !(level.getEntity(avatar.id) instanceof Player player)) continue;
			EntityType<?> look = Morph.look(player);
			if (look == null || FAILED.contains(look)) continue;
			// Any exception while extracting crashes the game
			try {
				float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(player));
				Mob mob = copy(level, player, look, partialTick);
				EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(mob, partialTick);
				animate(avatar, state);
				states.set(i, state);
				DRAWN.put(player.getId(), look);
			} catch (RuntimeException e) {
				FAILED.add(look);
				Tokio.LOGGER.error("Couldn't draw a player as {}; they're drawn as a player", look, e);
			}
		}
	}

	// The player's mob, moved where they are, with their items and pose
	private static Mob copy(ClientLevel level, Player player, EntityType<?> look, float partialTick) {
		Mob mob = COPIES.get(player.getId());
		if (mob == null || mob.getType() != look || mob.level() != level) {
			mob = Morph.make(level, look);
			if (mob == null) throw new IllegalStateException(look + " isn't a mob");
			// Never in the world, so it gets an id no entity there has: rendering reads it
			mob.setId(-1 - player.getId());
			COPIES.put(player.getId(), mob);
		}
		Vec3 position = player.getPosition(partialTick);
		float yRot = player.getYRot(partialTick);
		float xRot = player.getXRot(partialTick);
		mob.setPos(position);
		mob.setYRot(yRot);
		mob.setXRot(xRot);
		// The same old and new, so nothing lerps
		mob.setOldPosAndRot(position, yRot, xRot);
		mob.yBodyRot = mob.yBodyRotO = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
		mob.yHeadRot = mob.yHeadRotO = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
		mob.tickCount = player.tickCount;
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack held = player.getItemBySlot(slot);
			if (!ItemStack.matches(mob.getItemBySlot(slot), held)) mob.setItemSlot(slot, held.copy());
		}
		mob.setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
		mob.setInvisible(player.isInvisible());
		mob.setPose(player.getPose());
		mob.setShiftKeyDown(player.isShiftKeyDown());
		return mob;
	}

	// How the player walks, swings, crouches and gets hurt, which the mob doesn't do by itself
	private static void animate(AvatarRenderState avatar, EntityRenderState state) {
		state.ageInTicks = avatar.ageInTicks;
		state.outlineColor = avatar.outlineColor;
		state.displayFireAnimation = avatar.displayFireAnimation;
		state.isInvisible = avatar.isInvisible;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = avatar.bodyRot;
			living.yRot = avatar.yRot;
			living.xRot = avatar.xRot;
			living.walkAnimationPos = avatar.walkAnimationPos;
			living.walkAnimationSpeed = avatar.walkAnimationSpeed;
			living.hasRedOverlay = avatar.hasRedOverlay;
			living.deathTime = avatar.deathTime;
			living.isInWater = avatar.isInWater;
			living.isInvisibleToPlayer = avatar.isInvisibleToPlayer;
		}
		if (state instanceof ArmedEntityRenderState armed) {
			armed.swingAnimation = avatar.swingAnimation;
			armed.currentSwing = avatar.currentSwing;
		}
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isCrouching = avatar.isCrouching;
			humanoid.swimAmount = avatar.swimAmount;
		}
		if (state instanceof EnderDragonRenderState dragon) {
			dragon.hasRedOverlay = avatar.hasRedOverlay;
			dragon.deathTime = avatar.deathTime;
		}
	}
}
