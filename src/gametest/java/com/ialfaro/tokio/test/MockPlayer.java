package com.ialfaro.tokio.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** A survival player in the test's area, who isn't an op and keeps the chat lines they get. */
final class MockPlayer extends ServerPlayer {
	private final List<String> chat = new ArrayList<>();

	MockPlayer(GameTestHelper helper) {
		super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player"), ClientInformation.createDefault());
		GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
		helper.assertFalse(permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "The mock player has gamemaster permission");
	}

	@Override
	public GameType gameMode() {
		return GameType.SURVIVAL;
	}

	@Override
	public boolean isClientAuthoritative() {
		return false;
	}

	// Without a connection: the server player checks it, and sends effects through it
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return false;
	}

	@Override
	protected void onEffectAdded(MobEffectInstance effect, Entity source) {
	}

	@Override
	protected void onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, Entity source) {
	}

	@Override
	protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
	}

	@Override
	public void sendSystemMessage(Component message, boolean overlay) {
		chat.add(message.getString());
	}

	void assertNoChat(GameTestHelper helper) {
		helper.assertTrue(chat.isEmpty(), "The player got chat lines: " + chat);
	}
}
