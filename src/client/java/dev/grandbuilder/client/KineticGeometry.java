package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.client.EffectGeometry.Material;
import dev.grandbuilder.network.KineticBuildPayload;

public final class KineticGeometry {
	public record Pose(double x, double y, double z, double pitch, double yaw, double roll, double scale) { }
	private KineticGeometry() { }

	public static Pose pose(GrandBuilderClientEffects.KineticFrame frame, KineticBuildPayload.Cell cell) {
		var f = frame.scene();
		double x = cell.target().getX() + 0.5 - f.x(), y = cell.target().getY() + 0.5 - f.y(), z = cell.target().getZ() + 0.5 - f.z();
		double remaining = frame.setupRemaining() + ((cell.index() - frame.cursor()) / frame.budget() + 1.0) * frame.delay() - frame.cycleAge();
		double flight = f.mode() == BuildEffectMode.FLYING_BLOCKS ? 12 : 24;
		double t = smooth(1 - remaining / flight), rest = 1 - t;
		double a = random(cell.index(), 1) * Math.PI * 2, b = random(cell.index(), 2), c = random(cell.index(), 3);
		double radius = Math.min(36, Math.max(f.width(), f.depth()) * 0.7 + 6);
		return switch (f.mode()) {
			case FLYING_BLOCKS -> {
				if (remaining > flight + 2) yield null;
				double arc = Math.sin(t * Math.PI) * (3 + b * 4);
				yield new Pose(x * t + Math.cos(a) * radius * rest, y * t + (f.height() + 5 + b * 6) * rest + arc,
					z * t + Math.sin(a) * radius * rest, rest * rest * Math.PI * (1 + b), rest * Math.PI * 2, rest * c * 1.8, 1);
			}
			case REVERSE_COLLAPSE -> {
				double tremble = Math.sin(f.motionAge() * 1.8 + a) * 0.025 * rest;
				yield new Pose(x * t + Math.cos(a) * radius * b * rest + tremble,
					(0.5 + c * 0.35) * rest + y * t + Math.sin(t * Math.PI) * Math.min(12, f.height() * 0.4 + 2),
					z * t + Math.sin(a) * radius * b * rest, rest * (b - 0.5) * 2.5, rest * a, rest * (c - 0.5) * 2.5, 1);
			}
			case ASSEMBLY_WORKSHOP -> {
				if (remaining > flight + 3) yield null;
				double startX = Math.copySign(f.width() * 0.5 + 2.5, x == 0 ? b - 0.5 : x);
				double lift = smooth(t / 0.35), slide = smooth((t - 0.3) / 0.4), lower = smooth((t - 0.7) / 0.3);
				yield new Pose(mix(startX, x, slide), mix(mix(0.8, f.height() + 2, lift), y, lower),
					mix(-f.depth() * 0.5 - 2.5, z, slide), 0, (1 - slide) * Math.PI * 0.5, 0, 1);
			}
			case SCALE_MODEL -> {
				double unfold = smooth(1 - frame.setupRemaining() / Math.max(1, f.mode().setupTicks()));
				double scale = mix(0.14, 0.88, unfold), size = mix(scale, 1, t);
				double angle = (1 - unfold) * 0.32;
				double hinge = Math.sin(unfold * Math.PI) * 0.45, fx = x, fy = y, fz = z, pitch = 0, roll = 0;
				if (y > f.height() * 0.72) fy += Math.sin(unfold * Math.PI) * Math.min(4, f.height() * 0.3);
				else if (Math.abs(x) >= Math.max(0, f.width() * 0.5 - 1.1)) {
					fx += Math.copySign(y * Math.sin(hinge), x); fy = y * Math.cos(hinge); roll = -Math.copySign(hinge, x);
				} else if (Math.abs(z) >= Math.max(0, f.depth() * 0.5 - 1.1)) {
					fz += Math.copySign(y * Math.sin(hinge), z); fy = y * Math.cos(hinge); pitch = Math.copySign(hinge, z);
				}
				double rx = fx * Math.cos(angle) - fz * Math.sin(angle), rz = fx * Math.sin(angle) + fz * Math.cos(angle);
				yield new Pose(mix(rx * scale, x, t), mix(fy * scale + (1 - unfold) * 1.4, y, t), mix(rz * scale, z, t),
					pitch * rest, angle * rest, roll * rest, size);
			}
			default -> null;
		};
	}

	static void emit(GrandBuilderClientEffects.Frame f, EffectGeometry.Mesh m) {
		double deploy = smooth(f.motionAge() / Math.max(1, f.mode().setupTicks()));
		double retract = f.revealing() ? 1 - smooth(f.age() / 24) : 1;
		double w = Math.max(2, f.width() * 0.5 + 2), d = Math.max(2, f.depth() * 0.5 + 2);
		switch (f.mode()) {
			case FLYING_BLOCKS -> {
				for (int i = 0; i < 12; i++) {
					double a = i * Math.PI / 6 + f.motionAge() * 0.045;
					double r = f.radius() + 4, y = 1.5 + (i % 4) * 1.4;
					m.tube(Math.cos(a) * r, y, Math.sin(a) * r, Math.cos(a - 0.15) * (r + 1), y + 0.4,
						Math.sin(a - 0.15) * (r + 1), 0.025, 0x86DBEB, Material.GLOW, retract * 0.6);
				}
			}
			case REVERSE_COLLAPSE -> {
				for (int i = 0; i < 22; i++) {
					double a = i * 2.39996, r = (f.radius() + 2) * random(i, 7);
					m.push(Math.cos(a) * r, 0.2 + Math.pow(Math.sin(f.motionAge() * 0.04 + i), 2) * 2 * retract, Math.sin(a) * r);
					m.rotate(i * 0.4, f.motionAge() * 0.007 + i, i * 0.7);
					m.box(0.15 * retract, 0.08 * retract, 0.25 * retract, 0x747778, Material.SOLID, 1);
					m.pop();
				}
				m.push(0, 0.07, 0);
				m.torus(f.radius() * 0.7 + Math.sin(f.motionAge() * 0.13) * 0.2, 0.035, f.motionAge() * 0.02, 0xD6B77D, retract * 0.3);
				m.pop();
			}
			case ASSEMBLY_WORKSHOP -> {
				double h = (f.height() + 3) * deploy * retract;
				for (int side = -1; side <= 1; side += 2) for (int end = -1; end <= 1; end += 2) {
					box(m, side * w, h * 0.5, end * d, 0.28, h * 0.5 + 0.01, 0.28, 0x66716F);
					box(m, side * w, 0.12, end * d, 0.8, 0.12, 0.8, 0x303A3C);
					for (int rung = 0; rung < 4; rung++) {
						double y = h * (rung + 0.5) / 4;
						m.tube(side * w - 0.3, y, end * d - 0.3, side * w + 0.3, y + h / 5,
							end * d + 0.3, 0.05, 0xC5A54A, Material.SOLID, 1);
					}
				}
				for (int side = -1; side <= 1; side += 2) box(m, side * w, h, 0, 0.25, 0.25, d + 0.5, 0x697574);
				double bridgeZ = Math.sin(f.motionAge() * 0.05) * d * 0.8;
				box(m, 0, h, bridgeZ, w, 0.3, 0.3, 0xBFA24C);
				double trolleyX = Math.cos(f.motionAge() * 0.08) * w * 0.7, clawY = h - 1.2 - (1 + Math.sin(f.motionAge() * 0.08)) * deploy;
				box(m, trolleyX, h - 0.2, bridgeZ, 0.6, 0.4, 0.6, 0x374448);
				m.tube(trolleyX, h, bridgeZ, trolleyX, clawY, bridgeZ, 0.065, 0xBDC7C1, Material.SOLID, 1);
				for (int jaw = -1; jaw <= 1; jaw += 2) {
					m.tube(trolleyX, clawY, bridgeZ, trolleyX + jaw * 0.45, clawY - 0.45, bridgeZ, 0.13, 0xC5A54A, Material.SOLID, 1);
					m.tube(trolleyX + jaw * 0.45, clawY - 0.45, bridgeZ, trolleyX + jaw * 0.25, clawY - 0.8, bridgeZ, 0.09, 0x414D50, Material.SOLID, 1);
				}
				for (int side = -1; side <= 1; side += 2) {
					box(m, side * w, 0.5, -d - 1, 0.7, 0.2, 2, 0x394547);
					for (int roller = 0; roller < 8; roller++) {
						m.push(side * w, 0.73, -d - 2.75 + roller * 0.5);
						m.rotate(f.motionAge() * 0.2, 0, 0);
						m.box(0.64, 0.07, 0.07, 0x959F97, Material.SOLID, 1);
						m.pop();
					}
					m.tube(side * w, h + 0.5, 0, side * w, h + 0.7, 0, 0.12, 0xF2C75B, Material.GLOW, 0.8);
				}
			}
			case SCALE_MODEL -> {
				double size = mix(0.18, 1, deploy);
				box(m, 0, -0.13, 0, w * size, 0.1 * retract, d * size, 0x4E6263);
				for (int i = -4; i <= 4; i++) {
					m.tube(-w * size, 0.005, d * size * i / 4, w * size, 0.005, d * size * i / 4, 0.009, 0xACE7D5, Material.GLOW, 0.3 * retract);
					m.tube(w * size * i / 4, 0.005, -d * size, w * size * i / 4, 0.005, d * size, 0.009, 0xACE7D5, Material.GLOW, 0.3 * retract);
				}
				for (int side = -1; side <= 1; side += 2) {
					box(m, side * w * size, 0.3, -d * size, 0.15, 0.3, 0.15, 0x7D8984);
					m.tube(side * w * size, 0.65, -d * size, side * w * size, 1.1 + Math.sin(f.motionAge() * 0.1) * 0.1,
						-d * size, 0.025, 0xB8E9CB, Material.GLOW, retract);
				}
			}
			default -> { }
		}
	}

	private static void box(EffectGeometry.Mesh m, double x, double y, double z, double w, double h, double d, int color) {
		m.push(x, y, z); m.box(w, h, d, color, Material.SOLID, 1); m.pop();
	}
	private static double random(int index, int salt) {
		int n = index * 73428767 ^ salt * 912931;
		n = (n ^ n >>> 16) * 0x45d9f3b;
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}
	private static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }
	private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
