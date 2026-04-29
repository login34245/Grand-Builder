package dev.grandbuilder.client;

import dev.grandbuilder.network.BuildEffectPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

public final class GrandBuilderClientEffects {
	private static int totalTicks;
	private static int ticksLeft;
	private static int ageTicks;
	private static int phaseId;
	private static float intensity;
	private static float lastYawOffset;
	private static float lastPitchOffset;

	private GrandBuilderClientEffects() {
	}

	public static void trigger(BuildEffectPayload payload) {
		totalTicks = Math.max(1, payload.durationTicks());
		ticksLeft = totalTicks;
		ageTicks = 0;
		phaseId = payload.phaseId();
		intensity = Math.max(0.1f, payload.intensity());
		lastYawOffset = 0.0f;
		lastPitchOffset = 0.0f;
	}

	public static void tick(Minecraft client) {
		if (ticksLeft <= 0) {
			clearShake(client);
			return;
		}
		if (client.player == null) {
			clearState();
			return;
		}

		ageTicks++;
		applyCameraShake(client.player);
		ticksLeft--;
		if (ticksLeft <= 0) {
			clearShake(client);
		}
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (ticksLeft <= 0) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float age = ageTicks + partialTick;
		float remaining = clamp(ticksLeft / (float) Math.max(1, totalTicks), 0.0f, 1.0f);
		float revealBoost = phaseId == BuildEffectPayload.PHASE_REVEAL ? 1.45f : 1.0f;
		float wave = 0.5f + 0.5f * (float) Math.sin(age * 0.85f);
		float flicker = 0.5f + 0.5f * (float) Math.sin(age * 2.70f);
		float power = clamp((0.24f + wave * 0.42f + flicker * 0.18f) * intensity * revealBoost * remaining, 0.0f, 1.0f);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int shakeX = Math.round((float) Math.sin(age * 2.15f) * 5.0f * intensity * remaining * revealBoost);
		int shakeY = Math.round((float) Math.cos(age * 2.85f) * 3.0f * intensity * remaining * revealBoost);
		int topColor = argb(Math.round(90.0f * power), 95, 255, 238);
		int bottomColor = argb(Math.round(120.0f * power), 190, 65, 255);
		int flashAlpha = Math.round((phaseId == BuildEffectPayload.PHASE_REVEAL ? 130.0f : 55.0f) * power);

		graphics.pose().pushMatrix();
		graphics.pose().translate(shakeX, shakeY);
		graphics.fillGradient(-16, -16, width + 16, height + 16, topColor, bottomColor);
		graphics.fill(0, 0, width, height, argb(flashAlpha, 245, 255, 255));

		int beamWidth = Math.max(28, width / 11);
		int centerX = width / 2 + Math.round((float) Math.sin(age * 0.45f) * width * 0.06f);
		graphics.fill(centerX - beamWidth, 0, centerX + beamWidth, height, argb(Math.round(90.0f * power), 115, 255, 230));
		graphics.fill(centerX - beamWidth / 3, 0, centerX + beamWidth / 3, height, argb(Math.round(115.0f * power), 255, 255, 255));

		int scanAlpha = Math.round(56.0f * power);
		for (int y = -8; y < height + 16; y += 13) {
			int offset = Math.round((age * 2.0f) % 13.0f);
			graphics.fill(-8, y + offset, width + 8, y + offset + 2, argb(scanAlpha, 135, 255, 230));
		}
		graphics.pose().popMatrix();
	}

	private static void applyCameraShake(LocalPlayer player) {
		float remaining = clamp(ticksLeft / (float) Math.max(1, totalTicks), 0.0f, 1.0f);
		float revealBoost = phaseId == BuildEffectPayload.PHASE_REVEAL ? 1.65f : 1.0f;
		float power = intensity * remaining * revealBoost;
		float yawOffset = ((float) Math.sin(ageTicks * 1.75f) * 1.70f + (float) Math.sin(ageTicks * 4.20f) * 0.62f) * power;
		float pitchOffset = ((float) Math.cos(ageTicks * 1.45f) * 0.95f + (float) Math.sin(ageTicks * 3.35f) * 0.42f) * power;
		float yawDelta = yawOffset - lastYawOffset;
		float pitchDelta = pitchOffset - lastPitchOffset;

		player.setYRot(player.getYRot() + yawDelta);
		player.setXRot(clamp(player.getXRot() + pitchDelta, -89.0f, 89.0f));
		player.setYHeadRot(player.getYRot());
		lastYawOffset = yawOffset;
		lastPitchOffset = pitchOffset;
	}

	private static void clearShake(Minecraft client) {
		if ((lastYawOffset != 0.0f || lastPitchOffset != 0.0f) && client.player != null) {
			client.player.setYRot(client.player.getYRot() - lastYawOffset);
			client.player.setXRot(clamp(client.player.getXRot() - lastPitchOffset, -89.0f, 89.0f));
			client.player.setYHeadRot(client.player.getYRot());
		}
		clearState();
	}

	private static void clearState() {
		totalTicks = 0;
		ticksLeft = 0;
		ageTicks = 0;
		phaseId = 0;
		intensity = 0.0f;
		lastYawOffset = 0.0f;
		lastPitchOffset = 0.0f;
	}

	private static int argb(int alpha, int red, int green, int blue) {
		return (clamp(alpha, 0, 255) << 24) | (clamp(red, 0, 255) << 16) | (clamp(green, 0, 255) << 8) | clamp(blue, 0, 255);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
