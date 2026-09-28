package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.BuildCadence;
import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.HerobrineTiming;
import dev.grandbuilder.build.BuildStartSide;
import dev.grandbuilder.build.BuildOptions;
import dev.grandbuilder.build.DismantleStyle;
import dev.grandbuilder.network.BuildEffectPayload;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class EffectGeometryTest {
	public static void main(String[] args) {
		Set<Long> shapes = new HashSet<>();
		int samples = 0;
		for (BuildEffectMode mode : BuildEffectMode.values()) {
			if (mode == BuildEffectMode.STANDARD || mode == BuildEffectMode.HEROBRINE
				|| mode == BuildEffectMode.LIGHTNING || mode == BuildEffectMode.DISMANTLE) continue;
			for (double size : new double[] {1.0, 20.0, 256.0}) {
				for (int phase : new int[] {BuildEffectPayload.PHASE_ARRIVAL, BuildEffectPayload.PHASE_BUILD,
					BuildEffectPayload.PHASE_REVEAL, BuildEffectPayload.PHASE_PAUSED, BuildEffectPayload.PHASE_STOP}) {
					for (float age : new float[] {0.0f, 0.5f, 12.25f, 27.5f, 54.0f, 240.0f}) {
						Stats stats = sample(mode, phase, age, size, 1.0f);
						boolean expired = mode == BuildEffectMode.BUILDER_CHARGE && phase == BuildEffectPayload.PHASE_REVEAL && age >= 28;
						check(expired ? stats.count == 0 : stats.count > 100, "Missing geometry: " + mode);
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
		int actors = verifyHerobrine();
		int bolts = verifyLightning();
		verifyOptions();
		verifyBuildDirections();
		verifyCadence();
		check(BuildEffectMode.AURORA_WEAVE.networkId() == 5 && BuildEffectMode.HEROBRINE.networkId() == 6
			&& BuildEffectMode.BUILDER_CHARGE.networkId() == 7, "Existing mode IDs changed");
		check(BuildEffectMode.BUILDER_CHARGE.hidesSpeed() && !BuildEffectMode.HEROBRINE.hidesSpeed(), "Wrong speed controls");
		System.out.println("Effect geometry verified: " + samples + " scenes, " + actors + " actors, " + bolts + " bolts; standard cadence and mode options verified.");
	}

	private static int verifyHerobrine() {
		int samples = 0;
		for (int cycleDuration : new int[] {1,2,4,8,24}) {
			int duration = cycleDuration + 3;
			int contact = HerobrineTiming.contactTick(cycleDuration);
			check(contact == cycleDuration && contact < duration, "Contact must match the build cycle, not the visual tail");
			for (double[] target : new double[][] {{1.15, 0.5, 0}, {-1.15, 0.5, 0}, {0, 0.5, 1.15}, {0, 0.5, -1.15}}) {
				for (float age : new float[] {0, 0.5f, contact - 0.25f, contact, duration}) {
					Stats stats = actorSample(target, age, duration, contact, 1, false);
					check(stats.count > 500 && stats.count < 4000, "Actor mesh size");
					check(stats.max[1] - stats.min[1] >= 1.9, "Actor proportions");
					for (int count : stats.materialCounts) check(count % 4 == 0, "Incomplete actor quad");
					samples++;
				}
				Stats held = actorSample(target, contact - 0.25f, duration, contact, 1, false);
				Stats placed = actorSample(target, contact, duration, contact, 1, false);
				check(held.materialCounts[0] - placed.materialCounts[0] == 24, "Held block must disappear at contact");
				Stats ghost = actorSample(target, contact, duration, contact, 0.18f, true);
				check(ghost.materialCounts[0] == 0, "Afterimage writes solid depth");
				check(actorSample(target, contact, duration, contact, 0, false).maxAlpha == 0, "Actor opacity ignored");
			}
		}
		return samples;
	}

	private static void verifyCadence() {
		for (BuildSpeed speed : BuildSpeed.values()) for(int limit : new int[] {7,512}) {
			int budget = Math.min(limit,speed.defaultBlocksPerCycle());
			int delay = speed.defaultTickDelay();
			check(BuildCadence.budget(speed.defaultBlocksPerCycle(),limit)==budget,"Wrong per-cycle budget");
			for(int count : new int[] {1,36,513}) {
				int remaining=count,elapsed=0,ticks=0;
				long estimate=BuildCadence.estimateTicks(count,speed.defaultBlocksPerCycle(),delay,limit,0);
				while(remaining>0) {
					ticks++;
					if(++elapsed==delay) { remaining=Math.max(0,remaining-budget); elapsed=0; }
					check(estimate-ticks==BuildCadence.estimateTicks(remaining,speed.defaultBlocksPerCycle(),delay,limit,elapsed),
						"Countdown disagrees with standard placement: "+speed);
				}
			}
		}
		check(BuildCadence.estimateTicks(23546,512,1,512,0)==46,"Insane speed is not 512 blocks per tick");
		check(BuildCadence.estimateTicks(Integer.MAX_VALUE,1,40,512,0)==Integer.MAX_VALUE*40L,"ETA overflow");
		check(BuildCadence.estimateTicks(0,512,1,512,0)==0,"Completed build has nonzero ETA");
	}

	private static int verifyLightning() {
		int count=0;
		for(int sequence : new int[] {0,1,27,512}) for(int duration : new int[] {1,2,8,16}) {
			int contact=HerobrineTiming.contactTick(duration);
			for(float age : new float[] {0.5f,contact,contact+0.5f,contact+3,contact+5}) {
				Stats stats=new Stats();
				EffectGeometry.emitLightning(new GrandBuilderClientEffects.BoltFrame(0,64,0,sequence,age,contact,1,false),stats::accept);
				check(stats.count<4000,"Unbounded lightning");
				for(int vertices:stats.materialCounts) check(vertices%4==0,"Incomplete lightning quad");
				if(age==contact) {
					check(stats.count>500,"Missing strike geometry");
					for(int axis=0;axis<3;axis++) check(stats.max[axis]-stats.min[axis]>1,"Flat strike");
				}
				if(age==contact+5) check(stats.maxAlpha==0,"Strike does not fade");
				count++;
			}
		}
		return count;
	}

	private static void verifyOptions() {
		check(BuildStartSide.values().length==5,"Unexpected bottom build option");
		BuildOptions options=new BuildOptions(BuildStartSide.RIGHT,DismantleStyle.HEROBRINE,true);
		check(!options.normalized(BuildEffectMode.DISMANTLE).destructiveExplosion(),"Destructive dismantle leaked");
		check(options.normalized(BuildEffectMode.STANDARD).equals(BuildOptions.DEFAULT),"Hidden settings leaked");
		check(options.normalized(BuildEffectMode.REVERSE).startSide()==BuildStartSide.RIGHT,"Build side lost");
		check(options.normalized(BuildEffectMode.BUILDER_CHARGE).destructiveExplosion(),"Explosion option lost");
		check(options.normalized(BuildEffectMode.DISMANTLE).visualMode(BuildEffectMode.DISMANTLE)==BuildEffectMode.HEROBRINE,"Wrong removal actor");
		Stats before=new Stats(),after=new Stats();
		EffectGeometry.emitHerobrine(new GrandBuilderClientEffects.ActorFrame(0,64,0,1.15,0.5,0,0.75f,3,1,0xC9A36A,1,false,true),before::accept);
		EffectGeometry.emitHerobrine(new GrandBuilderClientEffects.ActorFrame(0,64,0,1.15,0.5,0,1,3,1,0xC9A36A,1,false,true),after::accept);
		check(after.materialCounts[0]-before.materialCounts[0]==24,"Removed block not taken into hand");
	}

	private static Stats actorSample(double[] target, float age, int duration, int contact, float opacity, boolean ghost) {
		Stats stats = new Stats();
		EffectGeometry.emitHerobrine(new GrandBuilderClientEffects.ActorFrame(0, 64, 0, target[0], target[1], target[2],
			age, duration, contact, 0xC9A36A, opacity, ghost), stats::accept);
		return stats;
	}

	private static void verifyBuildDirections() {
		for (int facing : new int[] {2,5,3,4}) for (BuildStartSide side : BuildStartSide.values()) {
			double width = 20, depth = 16;
			boolean swapped = facing == 5 || facing == 4;
			double dx = side == BuildStartSide.LEFT ? width+0.24 : side == BuildStartSide.RIGHT ? -width-0.24 : 0;
			double dy = side == BuildStartSide.TOP ? -8 : 0;
			double dz = side == BuildStartSide.FRONT ? depth+0.24 : side == BuildStartSide.BACK ? -depth-0.24 : 0;
			double[] expected = switch(facing) {
				case 5 -> new double[] {-dz,dy,dx};
				case 3 -> new double[] {-dx,dy,-dz};
				case 4 -> new double[] {dz,dy,-dx};
				default -> new double[] {dx,dy,dz};
			};
			double[][] centers = new double[2][3];
			for(int step=0;step<2;step++) {
				int[] count={0};
				double[] sum=centers[step];
				EffectGeometry.emit(new GrandBuilderClientEffects.Frame(BuildEffectMode.REVERSE,BuildEffectPayload.PHASE_BUILD,
					20,20,240,step,1,0,64,0,swapped?depth:width,8,swapped?width:depth,false,side.ordinal()+facing*8,false),
					(material,x,y,z,color)->{ if((color&0xFFFFFF)==0x8FFFF2){sum[0]+=x;sum[1]+=y;sum[2]+=z;count[0]++;} });
				check(count[0]>0,"Missing direction plane");
				for(int axis=0;axis<3;axis++) sum[axis]/=count[0];
			}
			for(int axis=0;axis<3;axis++) check(Math.abs(centers[1][axis]-centers[0][axis]-expected[axis])<0.02,
				"Direction plane disagrees with block order: facing="+facing+" side="+side);
		}
	}

	private static Stats sample(BuildEffectMode mode, int phase, float age, double size, float opacity) {
		Stats stats = new Stats();
		EffectGeometry.emit(new GrandBuilderClientEffects.Frame(mode, phase, age, age,
			phase == BuildEffectPayload.PHASE_REVEAL ? 28 : 240, mode==BuildEffectMode.REVERSE?Math.min(1,age/240):0.5f, opacity,
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
