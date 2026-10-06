package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.HerobrineTiming;
import dev.grandbuilder.network.BuildEffectPayload;
import dev.grandbuilder.network.HerobrinePlacementPayload;
import dev.grandbuilder.network.LightningStrikePayload;
import dev.grandbuilder.network.KineticBuildPayload;
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
import net.minecraft.world.level.block.Block;

public final class GrandBuilderClientEffects {
	private static final Map<UUID, Scene> SCENES = new LinkedHashMap<>();
	private static Identifier currentDimension;
	private static float impactAge = 100.0f;
	private static float impactPower;
	private static int impactColor = 0xD8FFFF;

	private GrandBuilderClientEffects() {
	}

	public record Frame(BuildEffectMode mode, int phase, float age, float motionAge, int duration, float progress,
		float opacity, double x, double y, double z, double width, double height, double depth,
		boolean dismantling, int orderId, boolean destructive) {
		public Frame(BuildEffectMode mode, int phase, float age, float motionAge, int duration, float progress,
			float opacity, double x, double y, double z, double width, double height, double depth) {
			this(mode,phase,age,motionAge,duration,progress,opacity,x,y,z,width,height,depth,false,0,false);
		}
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
		private Actor actor;
		private final List<Actor> ghosts = new ArrayList<>();
		private final List<Bolt> bolts = new ArrayList<>();
		private int movingPhase;
		private boolean blastTriggered;
		private KineticBuildPayload kinetic;
		private List<KineticBuildPayload.Cell> cells = List.of();
		private int kineticElapsed;
		private boolean cameraDismissed;

		private Scene(BuildEffectPayload payload) {
			this.payload = payload;
			this.age = payload.ageTicks();
			this.previousAge = age;
			this.motionAge = age;
			this.previousMotionAge = age;
			this.progress = clamp(payload.progress());
			this.previousProgress = progress;
			this.movingPhase = payload.phaseId() == BuildEffectPayload.PHASE_PAUSED
				? BuildEffectMode.byNetworkId(payload.effectModeId()).instantReveal() ? BuildEffectPayload.PHASE_ARRIVAL : BuildEffectPayload.PHASE_BUILD
				: payload.phaseId();
		}
	}

	public record ActorFrame(double x, double y, double z, double targetX, double targetY, double targetZ,
		float age, int duration, int contactTick, int blockColor, float opacity, boolean ghost, boolean dismantling) {
		public ActorFrame(double x, double y, double z, double targetX, double targetY, double targetZ,
			float age, int duration, int contactTick, int blockColor, float opacity, boolean ghost) {
			this(x,y,z,targetX,targetY,targetZ,age,duration,contactTick,blockColor,opacity,ghost,false);
		}
	}

	public record BoltFrame(double x, double y, double z, int sequence, float age, int contactTick, float opacity, boolean dismantling) {
	}

	public record KineticFrame(Frame scene, List<KineticBuildPayload.Cell> cells, int cursor, int budget,
		int delay, float cycleAge, float setupRemaining) { }

	public static void kinetic(KineticBuildPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !client.level.dimension().identifier().equals(payload.dimension())) return;
		Scene scene = SCENES.get(payload.sceneId());
		if (scene == null) return;
		scene.kinetic = payload;
		if (!payload.cells().isEmpty()) scene.cells = payload.cells();
		scene.kineticElapsed = 0;
	}

	public static List<KineticFrame> extractKinetic(float partialTick) {
		List<Frame> frames = extract(partialTick);
		List<KineticFrame> result = new ArrayList<>();
		int index = 0;
		for (Scene scene : SCENES.values()) {
			Frame frame = frames.get(index++);
			KineticBuildPayload p = scene.kinetic;
			if (p == null || frame.revealing() || scene.payload.phaseId() == BuildEffectPayload.PHASE_STOP) continue;
			boolean paused = scene.payload.phaseId() == BuildEffectPayload.PHASE_PAUSED;
			float elapsed = scene.kineticElapsed + (paused ? 0 : partialTick);
			float setup = Math.max(0, p.setupRemaining() - elapsed);
			float cycle = Math.min(Math.max(1, p.delay()) - 0.001f, p.cycleAge() + Math.max(0, elapsed - p.setupRemaining()));
			result.add(new KineticFrame(frame, scene.cells, p.cursor(), Math.max(1, p.budget()), Math.max(1, p.delay()), cycle, setup));
		}
		return List.copyOf(result);
	}

	private static final class Bolt {
		private final LightningStrikePayload payload;
		private float age;
		private float previousAge;
		private Bolt(LightningStrikePayload payload) { this.payload=payload; this.age=payload.age(); this.previousAge=age; }
	}

	public static void strike(LightningStrikePayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !client.level.dimension().identifier().equals(payload.dimension())) return;
		Scene scene = SCENES.get(payload.sceneId());
		if (scene == null || BuildEffectMode.byNetworkId(scene.payload.effectModeId()) != BuildEffectMode.LIGHTNING) return;
		for (Bolt bolt : scene.bolts) {
			if (bolt.payload.sequence() == payload.sequence()) {
				bolt.age = bolt.previousAge = Math.max(0,payload.age());
				return;
			}
		}
		if (scene.bolts.size() >= 64) scene.bolts.removeFirst();
		scene.bolts.add(new Bolt(payload));
	}

	private static final class Actor {
		private HerobrinePlacementPayload payload;
		private float age;
		private float previousAge;
		private int ghostAge;
		private final int blockColor;

		private Actor(HerobrinePlacementPayload payload, int blockColor) {
			this.payload = payload;
			this.age = Math.max(0, payload.age());
			this.previousAge = age;
			this.blockColor = blockColor;
		}
	}

	public static void place(HerobrinePlacementPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !client.level.dimension().identifier().equals(payload.dimension())) return;
		Scene scene = SCENES.get(payload.sceneId());
		if (scene == null || BuildEffectMode.byNetworkId(scene.payload.effectModeId()) != BuildEffectMode.HEROBRINE) return;
		if (scene.actor != null && scene.actor.payload.sequence() == payload.sequence()) {
			scene.actor.payload = payload;
			scene.actor.age = Math.max(0, payload.age());
			scene.actor.previousAge = scene.actor.age;
			return;
		}
		if (scene.actor != null) {
			if (scene.ghosts.size() >= 2) scene.ghosts.removeFirst();
			scene.ghosts.add(scene.actor);
		}
		int color = Block.stateById(payload.blockStateId()).getMapColor(client.level, payload.target()).col;
		scene.actor = new Actor(payload, color == 0 ? 0xA3ADB5 : color);
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
			scene = new Scene(payload);
			SCENES.put(payload.sceneId(), scene);
		} else {
			if (changedPhase) {
				scene.age = payload.ageTicks();
				scene.previousAge = scene.age;
			}
			scene.payload = payload;
			scene.staleTicks = 0;
		}
		if (BuildEffectMode.byNetworkId(payload.effectModeId()).cinematic()) {
			scene.age = scene.previousAge = Math.max(0, payload.ageTicks());
			if (payload.phaseId() != BuildEffectPayload.PHASE_REVEAL && payload.phaseId() != BuildEffectPayload.PHASE_STOP)
				scene.motionAge = scene.previousMotionAge = scene.age;
		}
		if (payload.phaseId() != BuildEffectPayload.PHASE_PAUSED) scene.movingPhase = payload.phaseId();
		boolean dismantleBlast = payload.dismantling() && BuildEffectMode.byNetworkId(payload.effectModeId()) == BuildEffectMode.BUILDER_CHARGE
			&& payload.phaseId() == BuildEffectPayload.PHASE_BUILD && !scene.blastTriggered;
		if (dismantleBlast) scene.blastTriggered = true;
		if ((changedPhase && payload.phaseId() == BuildEffectPayload.PHASE_REVEAL || dismantleBlast) && client.player != null) {
			double distance = client.player.position().distanceTo(new Vec3(
				(payload.min().getX() + payload.max().getX() + 1.0) * 0.5, payload.min().getY(),
				(payload.min().getZ() + payload.max().getZ() + 1.0) * 0.5));
			float power = Math.min(1.0f, payload.intensity() * 0.48f) * (float) clamp(1.0 - distance / 96.0);
			if (power >= impactPower || impactAge > 10.0f) {
				impactPower = power;
				impactAge = 0.0f;
				impactColor = switch (BuildEffectMode.byNetworkId(payload.effectModeId())) {
					case METEOR_FORGE, CLOCKWORK_GRID, BUILDER_CHARGE, ORBITAL_STRIKE -> 0xFFE1A3;
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
				scene.kineticElapsed++;
				scene.age++;
				scene.motionAge++;
				if (phase == BuildEffectPayload.PHASE_ARRIVAL) {
					scene.age = Math.min(scene.age, scene.payload.durationTicks());
				}
				if (scene.actor != null) {
					scene.actor.previousAge = scene.actor.age;
					scene.actor.age = Math.min(scene.actor.age + 1, scene.actor.payload.duration() + HerobrineTiming.RECOVERY_TICKS);
				}
				scene.ghosts.removeIf(actor -> ++actor.ghostAge >= 6);
				scene.bolts.removeIf(bolt -> {
					bolt.previousAge = bolt.age;
					return ++bolt.age > HerobrineTiming.contactTick(Math.max(1,bolt.payload.duration()))+5;
				});
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
			frames.add(new Frame(BuildEffectMode.byNetworkId(p.effectModeId()), scene.movingPhase, age,
				lerp(scene.previousMotionAge, scene.motionAge, partialTick),
				Math.max(1, p.durationTicks()), lerp(scene.previousProgress, scene.progress, partialTick), opacity,
				(p.min().getX() + p.max().getX() + 1.0) * 0.5, p.min().getY(),
				(p.min().getZ() + p.max().getZ() + 1.0) * 0.5,
				p.max().getX() - p.min().getX() + 1.0, p.max().getY() - p.min().getY() + 1.0,
				p.max().getZ() - p.min().getZ() + 1.0, p.dismantling(), p.orderId(), p.destructive()));
		}
		return List.copyOf(frames);
	}

	public static List<ActorFrame> extractActors(float partialTick) {
		List<ActorFrame> frames = new ArrayList<>();
		for (Scene scene : SCENES.values()) {
			int phase = scene.payload.phaseId();
			boolean paused = phase == BuildEffectPayload.PHASE_PAUSED;
			float age = lerp(scene.previousAge, scene.age, partialTick);
			float opacity = paused ? 0.35f : 1.0f;
			if (phase == BuildEffectPayload.PHASE_REVEAL || phase == BuildEffectPayload.PHASE_STOP) opacity *= clamp(1 - age / 5);
			if (scene.actor != null) frames.add(actorFrame(scene.actor, paused ? 1 : partialTick, opacity, false));
			for (Actor ghost : scene.ghosts) {
				frames.add(actorFrame(ghost, 1, opacity * 0.18f * clamp(1 - (ghost.ghostAge + (paused ? 0 : partialTick)) / 6), true));
			}
		}
		return List.copyOf(frames);
	}

	public static Frame cinematicFrame(float partialTick, UUID ownerId) {
		List<Frame> frames = extract(partialTick);
		int index = 0;
		Scene latest = null;
		Frame selected = null;
		for (Scene scene : SCENES.values()) {
			Frame frame = frames.get(index++);
			if (scene.payload.ownerId().equals(ownerId)) {
				latest = scene;
				selected = frame;
			}
		}
		// A previous build's outro must not reclaim the camera from a newer or paused job.
		return latest == null || !selected.mode().cinematic() || latest.cameraDismissed
			|| latest.payload.phaseId() == BuildEffectPayload.PHASE_PAUSED || latest.payload.phaseId() == BuildEffectPayload.PHASE_STOP
			? null : selected;
	}

	public static void dismissCinematic(UUID ownerId) {
		for (Scene scene : SCENES.values()) if (scene.payload.ownerId().equals(ownerId)) scene.cameraDismissed = true;
		EuropaCinematic.stopSounds();
	}

	static UUID cinematicSceneId(UUID ownerId) {
		UUID result = null;
		for (Scene scene : SCENES.values()) if (scene.payload.ownerId().equals(ownerId)) result = scene.payload.sceneId();
		return result;
	}

	private static ActorFrame actorFrame(Actor actor, float partialTick, float opacity, boolean ghost) {
		HerobrinePlacementPayload p = actor.payload;
		int duration = Math.max(1, Math.min(HerobrineTiming.MAX_WINDUP_TICKS, p.duration()));
		return new ActorFrame(p.x(), p.y(), p.z(), p.target().getX() + 0.5 - p.x(),
			p.target().getY() + 0.5 - p.y(), p.target().getZ() + 0.5 - p.z(),
			lerp(actor.previousAge, actor.age, partialTick), duration + HerobrineTiming.RECOVERY_TICKS, HerobrineTiming.contactTick(duration),
			actor.blockColor, opacity, ghost, p.dismantling());
	}

	public static List<BoltFrame> extractBolts(float partialTick) {
		List<BoltFrame> frames = new ArrayList<>();
		for (Scene scene : SCENES.values()) {
			int phase = scene.payload.phaseId();
			float opacity = phase == BuildEffectPayload.PHASE_PAUSED ? 0.35f : 1;
			if (phase == BuildEffectPayload.PHASE_STOP) opacity *= clamp(1-scene.age/5);
			for (Bolt bolt : scene.bolts) {
				LightningStrikePayload p = bolt.payload;
				frames.add(new BoltFrame(p.target().getX()+0.5,p.target().getY()+0.5,p.target().getZ()+0.5,p.sequence(),
					lerp(bolt.previousAge,bolt.age,phase == BuildEffectPayload.PHASE_PAUSED ? 1 : partialTick),
					HerobrineTiming.contactTick(Math.max(1,p.duration())), opacity, p.dismantling()));
			}
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
