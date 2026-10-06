package dev.grandbuilder.client;

import dev.grandbuilder.build.EuropaTimeline;
import net.minecraft.world.phys.Vec3;
import static dev.grandbuilder.client.EffectGeometry.Material.*;

public final class EuropaGeometry {
	public record View(Vec3 eye, Vec3 target) { }
	private static final double TAU = Math.PI * 2;
	private EuropaGeometry() { }
	public static View view(double age) {
		int shot = EuropaTimeline.shot(age);
		double t = switch (shot) {
			case 1 -> smooth((age - 60) / 68);
			case 2 -> smooth((age - 128) / 84);
			case 3 -> smooth((age - 212) / 68);
			default -> smooth((age - 280) / 80);
		};
		return switch (shot) {
			case 1 -> new View(new Vec3(19 - t * 5, 6 + t * 2, 29 - t * 3), new Vec3(-2, 0, 0));
			case 2 -> new View(new Vec3(9 - t * 2, 5, 18 - t * 2), Vec3.ZERO);
			case 3 -> new View(new Vec3(11 - t * 3, 6 - t * 2, 17 - t * 2), new Vec3(0, 0.8, 0));
			default -> new View(new Vec3(39 - t * 3, 45 - t * 2, 43 - t * 2), new Vec3(-5, 10, -5));
		};
	}
	public static void space(double age, EffectGeometry.Sink sink) {
		EffectGeometry.Mesh m = new EffectGeometry.Mesh(sink, 1);
		int shot = EuropaTimeline.shot(age);
		if (shot == 1) {
			m.push(-6, 0, 0); m.rotate(0.12, age * 0.0015, -0.09); planet(m, 9, 0); m.pop();
			m.push(10, 1, -2); m.rotate(0, age * 0.004, 0); planet(m, 2.4, 1); m.pop();
			relay(m, 5, 3, 6, age);
			double p = (age - 60) / 68;
			for (int i = 0; i < 8; i++) {
				double u = Math.clamp(p * 1.5 - i * 0.045, 0, 1);
				m.push(5 + u * 5, 3 - u * 2, 6 - u * 8);
				m.box(0.08, 0.08, 0.08, 0xBAFFC9, GLOW, 1 - i * 0.09); m.pop();
			}
		} else if (shot == 2) {
			m.push(-14, 2, -20); m.rotate(0.08, age * 0.001, 0); planet(m, 11, 0); m.pop();
			m.push(0, 0, 0); m.rotate(0, age * 0.0015, -0.1); planet(m, 5, 1); m.pop();
			relay(m, 4.5, 4.5, 1, age);
			m.push(1.8, 2, 4.4); m.box(0.48, 0.13, 0.35, 0x34454B, SOLID, 1);
			m.box(0.38, 0.16, 0.12, 0xFFCA72, GLOW, 0.6 + Math.sin(age * 0.16) * 0.25); m.pop();
		} else if (shot == 3) {
			m.push(0, 0, 0); station(m, age); m.pop();
		} else if (shot == 4) {
			earth(m, age);
			m.push(-15, 24, -15); m.scale(0.8); m.rotate(0, Math.PI / 4, 0); m.rotate(0.95, 0, 0); station(m, age); m.pop();
			double p = smooth((age - 286) / (EuropaTimeline.CONTACT - 286));
			if (p > 0) {
				Vec3 start = new Vec3(-12.457, 21.784, -12.457), end = new Vec3(0, 8.8, 0);
				Vec3 tip = start.lerp(end, p);
				// Short segments keep the beam's depth order correct where it meets the planet.
				for (int segment = 0; segment < 32; segment++) {
					Vec3 a = start.lerp(tip, segment / 32.0), b = start.lerp(tip, (segment + 1) / 32.0);
					for (int i = 0; i < 3; i++) m.tube(a.x, a.y, a.z, b.x, b.y, b.z,
						i == 0 ? 0.16 : i == 1 ? 0.38 : 0.7, i == 0 ? 0xFFFFFF : 0x7BFF8F, i == 0 ? SOLID : GLOW, i == 0 ? 1 : 0.22);
				}
				if (age >= EuropaTimeline.CONTACT) {
					double hit = (age - EuropaTimeline.CONTACT) / 22;
					m.push(0, 8.88 + hit * 0.5, 0);
					for (int i = 0; i < 3; i++) m.rectangle(0.5 + hit * (7 + i * 2), 0.5 + hit * (7 + i * 2), 0.04,
						0xC6FFCB, 1 - i * 0.2);
					m.pop();
					for (int i = 0; i < 18; i++) {
						double a = i * TAU / 18;
						m.tube(0, 8.9, 0, Math.cos(a) * hit * 7, 9 + Math.sin(hit * Math.PI) * 2, Math.sin(a) * hit * 7,
							0.025, 0xC6FFCB, GLOW, 0.8);
					}
				}
			}
		}
	}
	private static void relay(EffectGeometry.Mesh m, double x, double y, double z, double age) {
		m.push(x, y, z); m.rotate(-0.25, age * 0.009, 0.15);
		m.box(0.28, 0.38, 0.3, 0xA9B2BB, SOLID, 1);
		for (int side : new int[] {-1, 1}) {
			m.push(side * 1.15, 0, 0); m.box(0.82, 0.045, 0.48, 0x294A6C, SOLID, 1);
			for (int i = 0; i < 6; i++) m.tube(-0.7 + i * 0.28, 0.05, -0.48, -0.7 + i * 0.28, 0.05, 0.48, 0.009, 0xA5BED7, SOLID, 1);
			m.pop();
		}
		m.tube(0, 0.3, 0, 0, 1.3, 0, 0.025, 0xCCD4D9, SOLID, 1);
		m.pop();
	}
	private static double[] point(double r, double lat, double lon) {
		return new double[] {r * Math.cos(lat) * Math.cos(lon), r * Math.sin(lat), r * Math.cos(lat) * Math.sin(lon)};
	}
	private static void planet(EffectGeometry.Mesh m, double radius, int kind) {
		for (int row = 0; row < 32; row++) for (int column = 0; column < 64; column++) {
			double lat = -Math.PI / 2 + row * Math.PI / 32, lon = column * TAU / 64;
			double nextLat = lat + Math.PI / 32, nextLon = lon + TAU / 64;
			int color;
			if (kind == 0) {
				double bands = Math.sin(lat * 18 + Math.sin(lon * 5) * 0.22) + Math.sin(lat * 37 + lon * 2) * 0.25;
				color = bands > 0.5 ? 0xCFAD91 : bands < -0.4 ? 0x986652 : 0xE8D4AF;
				if (Math.pow((lat + 0.22) / 0.14, 2) + Math.pow(Math.sin(lon - 0.85) / 0.3, 2) < 1) color = 0xBE7460;
			} else if (kind == 1) {
				double crack = Math.abs(Math.sin(lon * 17 + lat * 6 + Math.sin(lat * 11) * 0.8));
				color = crack < 0.12 ? 0x9C7865 : crack < 0.22 ? 0xB8AAA0 : 0xD7D8CC;
			} else {
				// Recessed equatorial trench and irregular hull panels, never a glowing plain sphere.
				double[] center = point(radius, (lat + nextLat) / 2, (lon + nextLon) / 2);
				if (center[2] > 3 && center[0] * center[0] / 2.5 + (center[1] - 1.7) * (center[1] - 1.7) / 2.5 < 1) continue;
				color = Math.abs(lat) < 0.06 ? 0x292E32 : (column * 17 + row * 23) % 7 < 2 ? 0x60666A : 0x8D9499;
				if ((column + row * 3) % 13 == 0) color = 0xA8ABAA;
			}
			double light = 0.24 + 0.76 * Math.max(0, Math.cos(lat) * Math.cos(lon - 0.9) * 0.86 + Math.sin(lat) * 0.45);
			m.quad(SOLID, point(radius, lat, lon), point(radius, lat, nextLon), point(radius, nextLat, nextLon),
				point(radius, nextLat, lon), shade(color, light), 1);
		}
	}
	private static void station(EffectGeometry.Mesh m, double age) {
		planet(m, 4.5, 2);
		m.push(0, 1.7, 3.82); m.rotate(-0.32, 0, 0);
		for (int ring = 0; ring < 6; ring++) for (int i = 0; i < 48; i++) {
			double r0 = ring / 6.0 * 1.5, r1 = (ring + 1) / 6.0 * 1.5;
			double a = i * TAU / 48, b = (i + 1) * TAU / 48;
			m.quad(SOLID, new double[] {Math.cos(a) * r0, Math.sin(a) * r0, -0.4 + r0 * 0.32},
				new double[] {Math.cos(b) * r0, Math.sin(b) * r0, -0.4 + r0 * 0.32},
				new double[] {Math.cos(b) * r1, Math.sin(b) * r1, -0.4 + r1 * 0.32},
				new double[] {Math.cos(a) * r1, Math.sin(a) * r1, -0.4 + r1 * 0.32}, ring % 2 == 0 ? 0x303E38 : 0x62766B, 1);
		}
		double charge = smooth((age - 225) / 48);
		for (int i = 0; i < 8; i++) {
			double a = i * TAU / 8;
			m.tube(Math.cos(a) * 1.4, Math.sin(a) * 1.4, 0.12, 0, 0, 1.1 * charge,
				0.025 + charge * 0.018, 0x8EFF9B, GLOW, charge * 0.8);
		}
		m.push(0, 0, 1.1 * charge); m.box(0.07 + charge * 0.15, 0.07 + charge * 0.15, 0.07, 0xDDFFE0, GLOW, charge); m.pop();
		m.pop();
	}
	private static void earth(EffectGeometry.Mesh m, double age) {
		// The surface is tiled below; a single large top face would cover the incoming beam.
		double[][] body = {{-8,-8,-8},{8,-8,-8},{8,8,-8},{-8,8,-8},{-8,-8,8},{8,-8,8},{8,8,8},{-8,8,8}};
		int[][] walls = {{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{4,5,1,0}};
		for (int i = 0; i < walls.length; i++) {
			int[] q = walls[i];
			m.quad(SOLID, body[q[0]], body[q[1]], body[q[2]], body[q[3]], shade(0x294D71, 0.65 + (i % 3) * 0.15), 1);
		}
		for (int x = -8; x < 8; x++) for (int z = -8; z < 8; z++) {
			double land = Math.sin(x * 0.7) + Math.cos(z * 0.55) + Math.sin(x * 0.24 + z * 0.6);
			double h = land > 0.45 ? 0.3 + Math.max(0, land - 1.3) * 0.5 : 0.03;
			m.push(x + 0.5, 8 + h * 0.5, z + 0.5);
			m.box(0.5, h * 0.5, 0.5, land > 1.8 ? 0xB3A98C : land > 0.45 ? 0x699066 : 0x417995, SOLID, 1); m.pop();
			if ((x * x + z * 17 + 99) % 19 == 0) {
				m.push(x + Math.sin(age * 0.003) * 0.5, 10 + (x & 1) * 0.15, z);
				m.box(1.2, 0.05, 0.5, 0xDCE4E7, VEIL, 0.75); m.pop();
			}
		}
		for (int side = 0; side < 4; side++) {
			m.push(0, 0, 0); m.rotate(0, side * Math.PI / 2, 0);
			for (int row = 0; row < 8; row++) {
				m.push(0, 6.8 - row * 1.9, 8.01); m.box(8, 0.05, 0.02, row < 3 ? 0x4E657B : 0x5B5150, SOLID, 1); m.pop();
			}
			m.pop();
		}
	}
	static void site(GrandBuilderClientEffects.Frame f, EffectGeometry.Mesh m) {
		double age = f.revealing() ? EuropaTimeline.ARRIVAL + f.age() : f.age();
		double w = Math.min(64, f.width() * 0.53 + 1), d = Math.min(64, f.depth() * 0.53 + 1);
		m.push(0, 0.16, 0); m.rectangle(w, d, 0.04, 0x7EFF99, 0.35 + Math.sin(age * 0.11) * 0.2); m.pop();
		for (int i = 0; i < 4; i++) {
			double x = (i < 2 ? -1 : 1) * w, z = ((i & 1) == 0 ? -1 : 1) * d;
			m.tube(x, 0.15, z, x, 1.6 + Math.sin(age * 0.08 + i) * 0.25, z, 0.06, 0xAFFFBB, GLOW, 0.7);
		}
		if (age >= EuropaTimeline.CONTACT && age < EuropaTimeline.ARRIVAL + 24) {
			double power = f.revealing() ? 1 - smooth(f.age() / 24) : smooth((age - EuropaTimeline.CONTACT) / 12);
			m.tube(0, 0, 0, 0, Math.min(96, f.height() + 42), 0, 0.2 + power * 0.3, 0xE5FFE8, GLOW, power);
			m.push(0, 0.3, 0); m.rectangle(w * (1 + power), d * (1 + power), 0.08, 0x7EFF99, power); m.pop();
		}
	}
	static double smooth(double t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
	private static int shade(int rgb, double k) {
		return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
	}
}
