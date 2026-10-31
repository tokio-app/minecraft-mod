package com.ialfaro.tokio.client;

import com.ialfaro.tokio.game.Laser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * Block displays as the game draws them, but a laser's beams (named
 * {@link Laser#BEAM}) as beacon beams: glowing, in the beam's color, their
 * texture running down to the spot, with bright pulses sliding down them. The
 * server keeps them plain block displays, so players without the mod see those.
 */
final class LaserRenderer extends DisplayRenderer.BlockDisplayRenderer {
	// How fast the texture runs and the beam turns, against a beacon's; less
	// than 0 runs it down
	private static final float SPEED = -4;
	// Pulses slide down the beam's lower part: this many, over this many
	// blocks, this many blocks a tick
	private static final int PULSES = 3;
	private static final float PULSE_SPAN = 36;
	private static final float PULSE_SPEED = 1;

	LaserRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public BlockDisplayEntityRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Display.BlockDisplay entity, BlockDisplayEntityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		State laser = (State) state;
		Component name = entity.getCustomName();
		laser.beam = name != null && Laser.BEAM.equals(name.getString());
		BlockState block = entity.getBlockState();
		// The super laser's glow is only a halo around the white beam
		laser.halo = block.is(Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
		laser.color = block.is(Blocks.CONCRETE.pick(DyeColor.RED)) ? 0xFFFF3030 : laser.halo ? 0xFF60C8FF : 0xFFFFFFFF;
	}

	@Override
	public void submit(BlockDisplayEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (!(state instanceof State laser) || !laser.beam || state.renderState == null) {
			super.submit(state, poseStack, submitNodeCollector, camera);
			return;
		}
		// The block's shape: its corner, the beam's lean and turn, and its width and length
		Transformation shape = state.renderState.transformation().get(state.interpolationProgress);
		Vector3fc corner = shape.translation();
		float width = shape.scale().x();
		float length = shape.scale().y();
		if (width <= 0 || length <= 0) return;
		poseStack.pushPose();
		poseStack.translate(corner.x(), corner.y(), corner.z());
		poseStack.rotate(shape.leftRotation());
		// A beacon beam stands in the middle of its block
		poseStack.translate(width / 2 - 0.5F, 0, width / 2 - 0.5F);
		// The aim beam is thin, so players without the mod see a small block;
		// a beacon beam that thin would hardly show
		float drawn = width < 0.5F ? width * 2 : width;
		float time = state.ageInTicks * SPEED;
		// A halo's solid part, which a beacon beam always has, too thin to see
		float solid = laser.halo ? 0.001F : drawn * 0.5F;
		BeaconRenderer.submitBeaconBeam(poseStack, submitNodeCollector, BeaconRenderer.BEAM_LOCATION, 1, time, 0, Mth.ceil(length), laser.color, solid, drawn * (laser.halo ? 0.6F : 0.9F));
		if (!laser.halo) {
			float span = Math.min(length, PULSE_SPAN);
			for (int i = 0; i < PULSES; i++) {
				float up = span - Mth.positiveModulo(state.ageInTicks * PULSE_SPEED + i * span / PULSES, span);
				poseStack.pushPose();
				poseStack.translate(0, up, 0);
				BeaconRenderer.submitBeaconBeam(poseStack, submitNodeCollector, BeaconRenderer.BEAM_LOCATION, 1, time, 0, 1, laser.color, drawn * 0.7F, drawn * 1.2F);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}

	private static final class State extends BlockDisplayEntityRenderState {
		boolean beam;
		boolean halo;
		int color;
	}
}
