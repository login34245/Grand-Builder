package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.network.BuildEffectPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class GrandBuilderClientEffects {
	private static final Map<UUID, Scene> SCENES = new LinkedHashMap<>();
	private static Identifier currentDimension;
	private static float impactAge = 100.0f;
	private static float impactPower;
	private static int impactColor = 0xD8FFFF;

	private GrandBuilderClientEffects() {
	}

	public record Frame(BuildEffectMode mode, int phase, float age, float motionAge, int duration, float progress,
		float opacity, double x, double y, double z, double width, double height, double depth) {
		public boolean revealing() {
			return phase == BuildEffectPayload.PHASE_REVEAL;
		}
		public float phaseProgress() {
			return clamp(age / Math.max(1, duration));
		}
		public double radius() {
			return Math.max(4.0, Math.min(24.0, Math.max(width, depth) * 0.55));
		}
	}

	private static final class Scene {
		private BuildEffectPayload payload;
		private float age;
		private float previousAge;
		private float motionAge;
		private float previousMotionAge;
		private float progress;
		private float previousProgress;
		private int staleTicks;

		private Scene(BuildEffectPayload payload) {
			this.payload = payload;
			this.age = payload.ageTicks();
			this.previousAge = age;
			this.motionAge = age;
			this.previousMotionAge = age;
			this.progress = clamp(payload.progress());
			this.previousProgress = progress;
		}
	}

	public static void trigger(BuildEffectPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !client.level.dimension().identifier().equals(payload.dimension())) {
			return;
		}
		if (!payload.dimension().equals(currentDimension)) {
			clear();
			currentDimension = payload.dimension();
		}
		Scene scene = SCENES.get(payload.sceneId());
		boolean changedPhase = scene == null || scene.payload.phaseId() != payload.phaseId();
		if (scene == null) {
			if (SCENES.size() >= 16) {
				SCENES.remove(SCENES.keySet().iterator().next());
			}
			SCENES.put(payload.sceneId(), new Scene(payload));
		} else {
			if (changedPhase) {
				scene.age = payload.ageTicks();
				scene.previousAge = scene.age;
			}
			scene.payload = payload;
			scene.staleTicks = 0;
		}
		if (changedPhase && payload.phaseId() == BuildEffectPayload.PHASE_REVEAL && client.player != null) {
			double distance = client.player.position().distanceTo(new Vec3(
				(payload.min().getX() + payload.max().getX() + 1.0) * 0.5, payload.min().getY(),
				(payload.min().getZ() + payload.max().getZ() + 1.0) * 0.5));
			float power = Math.min(1.0f, payload.intensity() * 0.48f) * (float) clamp(1.0 - distance / 96.0);
			if (power >= impactPower || impactAge > 10.0f) {
				impactPower = power;
				impactAge = 0.0f;
				impactColor = switch (BuildEffectMode.byNetworkId(payload.effectModeId())) {
					case METEOR_FORGE, CLOCKWORK_GRID -> 0xFFE1A3;
					case RIFT_BLOOM -> 0xEBD2FF;
					default -> 0xD8FFFF;
				};
			}
		}
	}

	public static void tick(Minecraft client) {
		if (client.level == null || !client.level.dimension().identifier().equals(currentDimension)) {
			clear();
			return;
		}
		if (client.isPaused()) {
			return;
		}
		impactAge++;
		SCENES.values().removeIf(scene -> {
			scene.previousAge = scene.age;
			scene.previousMotionAge = scene.motionAge;
			scene.previousProgress = scene.progress;
			int phase = scene.payload.phaseId();
			if (phase != BuildEffectPayload.PHASE_PAUSED) {
				scene.age++;
				scene.motionAge++;
				if (phase == BuildEffectPayload.PHASE_ARRIVAL) {
					scene.age = Math.min(scene.age, scene.payload.durationTicks());
				}
			}
			scene.progress += (clamp(scene.payload.progress()) - scene.progress) * 0.35f;
			scene.staleTicks++;
			boolean ending = phase == BuildEffectPayload.PHASE_REVEAL || phase == BuildEffectPayload.PHASE_STOP;
			return ending ? scene.age >= scene.payload.durationTicks() : scene.staleTicks > 60;
		});
	}

	public static List<Frame> extract(float partialTick) {
		List<Frame> frames = new ArrayList<>(SCENES.size());
		for (Scene scene : SCENES.values()) {
			BuildEffectPayload p = scene.payload;
			float age = lerp(scene.previousAge, scene.age, partialTick);
			float opacity = Math.min(1.0f, age / 6.0f);
			if (p.phaseId() == BuildEffectPayload.PHASE_REVEAL) {
				opacity = clamp((p.durationTicks() - age) / 8.0f);
			} else if (p.phaseId() == BuildEffectPayload.PHASE_STOP) {
				opacity = clamp(1.0f - age / p.durationTicks());
			} else if (p.phaseId() == BuildEffectPayload.PHASE_PAUSED) {
				opacity = 0.28f;
			}
			frames.add(new Frame(BuildEffectMode.byNetworkId(p.effectModeId()), p.phaseId(), age,
				lerp(scene.previousMotionAge, scene.motionAge, partialTick),
				Math.max(1, p.durationTicks()), lerp(scene.previousProgress, scene.progress, partialTick), opacity,
				(p.min().getX() + p.max().getX() + 1.0) * 0.5, p.min().getY(),
				(p.min().getZ() + p.max().getZ() + 1.0) * 0.5,
				p.max().getX() - p.min().getX() + 1.0, p.max().getY() - p.min().getY() + 1.0,
				p.max().getZ() - p.min().getZ() + 1.0));
		}
		return List.copyOf(frames);
	}

	public static float shake(float partialTick) {
		float age = impactAge + partialTick;
		return age < 14.0f ? impactPower * (float) Math.exp(-age * 0.24) : 0.0f;
	}

	public static float shakeAge(float partialTick) {
		return impactAge + partialTick;
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		float age = impactAge + deltaTracker.getGameTimeDeltaPartialTick(false);
		if (age < 4.0f && impactPower > 0.0f) {
			int alpha = Math.round(24.0f * impactPower * clamp(1.0f - age / 4.0f));
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | impactColor);
		}
	}

	private static void clear() {
		SCENES.clear();
		currentDimension = null;
		impactAge = 100.0f;
		impactPower = 0.0f;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float clamp(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
