package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.network.BuildEffectPayload;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class EffectGeometryTest {
	public static void main(String[] args) {
		Set<Long> shapes = new HashSet<>();
		int samples = 0;
		for (BuildEffectMode mode : BuildEffectMode.values()) {
			if (mode == BuildEffectMode.STANDARD) continue;
			for (double size : new double[] {1.0, 20.0, 256.0}) {
				for (int phase : new int[] {BuildEffectPayload.PHASE_ARRIVAL, BuildEffectPayload.PHASE_BUILD,
					BuildEffectPayload.PHASE_REVEAL, BuildEffectPayload.PHASE_PAUSED, BuildEffectPayload.PHASE_STOP}) {
					for (float age : new float[] {0.0f, 0.5f, 12.25f, 27.5f, 54.0f, 240.0f}) {
						Stats stats = sample(mode, phase, age, size, 1.0f);
						check(stats.count > 100, "Missing geometry: " + mode);
						check(stats.count < 20000, "Unbounded geometry: " + mode);
						for (int count : stats.materialCounts) check(count % 4 == 0, "Incomplete quad");
						samples++;
					}
				}
			}
			Stats a = sample(mode, BuildEffectPayload.PHASE_ARRIVAL, 16.0f, 20.0, 1.0f);
			Stats b = sample(mode, BuildEffectPayload.PHASE_ARRIVAL, 31.5f, 20.0, 1.0f);
			check(a.fingerprint != b.fingerprint, "Static animation: " + mode);
			for (int axis = 0; axis < 3; axis++) check(a.max[axis] - a.min[axis] > 1.0f, "Flat geometry: " + mode);
			check(a.materialCounts[EffectGeometry.Material.GLOW.ordinal()] > 0, "Missing light layer");
			check(shapes.add(a.fingerprint), "Duplicate scene: " + mode);
			Stats invisible = sample(mode, BuildEffectPayload.PHASE_BUILD, 20.0f, 20.0, 0.0f);
			check(invisible.maxAlpha == 0, "Opacity ignored");
		}
		Stats standard = sample(BuildEffectMode.STANDARD, BuildEffectPayload.PHASE_BUILD, 20, 20, 1);
		check(standard.count == 0, "Standard mode changed");
		System.out.println("Effect geometry verified: " + samples + " phase/size/frame samples, 5 distinct moving 3D scenes.");
	}

	private static Stats sample(BuildEffectMode mode, int phase, float age, double size, float opacity) {
		Stats stats = new Stats();
		EffectGeometry.emit(new GrandBuilderClientEffects.Frame(mode, phase, age, age,
			phase == BuildEffectPayload.PHASE_REVEAL ? 28 : 240, 0.5f, opacity,
			0, 64, 0, size, size * 0.75, size * 0.8), stats::accept);
		return stats;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static final class Stats {
		private int count;
		private int maxAlpha;
		private long fingerprint = 17;
		private final int[] materialCounts = new int[3];
		private final float[] min = new float[3];
		private final float[] max = new float[3];

		private Stats() {
			Arrays.fill(min, Float.POSITIVE_INFINITY);
			Arrays.fill(max, Float.NEGATIVE_INFINITY);
		}

		private void accept(EffectGeometry.Material material, float x, float y, float z, int color) {
			check(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Invalid vertex");
			float[] values = {x, y, z};
			for (int axis = 0; axis < 3; axis++) {
				min[axis] = Math.min(min[axis], values[axis]);
				max[axis] = Math.max(max[axis], values[axis]);
				fingerprint = fingerprint * 31 + Float.floatToIntBits(values[axis]);
			}
			maxAlpha = Math.max(maxAlpha, color >>> 24);
			materialCounts[material.ordinal()]++;
			count++;
		}
	}
}
