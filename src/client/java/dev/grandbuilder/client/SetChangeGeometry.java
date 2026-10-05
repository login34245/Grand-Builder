package dev.grandbuilder.client;

import dev.grandbuilder.client.EffectGeometry.Material;
import dev.grandbuilder.network.KineticBuildPayload;

/** A physical stage rig: folding wings, travelling portals and an architectural match cut. */
public final class SetChangeGeometry {
	private SetChangeGeometry() { }

	static KineticGeometry.Pose blockPose(GrandBuilderClientEffects.KineticFrame frame, KineticBuildPayload.Cell cell,
		double x, double y, double z, double remaining) {
		var f = frame.scene();
		if (remaining > 36 && frame.setupRemaining() <= 0) return null;
		double t = smooth(1 - remaining / 36), rest = 1 - t;
		int sector = Math.floorMod(cell.target().getX() / 4 + cell.target().getZ() / 4, 4);
		double angle = sector * Math.PI / 2, radius = Math.min(38, Math.max(f.width(), f.depth()) * 0.6 + 8);
		double deploy = smooth(1 - frame.setupRemaining() / 96);
		double startX = Math.cos(angle) * radius + x * 0.28, startZ = Math.sin(angle) * radius + z * 0.28;
		double startY = 3 + y * 0.28 + Math.sin(deploy * Math.PI) * 3;
		double curl = Math.sin(t * Math.PI) * (2 + sector * 0.5);
		return new KineticGeometry.Pose(mix(startX, x, t) + Math.sin(angle) * curl,
			mix(startY, y, t) + Math.sin(t * Math.PI) * 3, mix(startZ, z, t) - Math.cos(angle) * curl,
			rest * 0.35, rest * (angle + Math.PI * 2 * smooth(t)), rest * -0.18,
			mix(0.28, 1, t));
	}

	static void emit(GrandBuilderClientEffects.Frame f, EffectGeometry.Mesh m) {
		double w = Math.max(4, f.width() * 0.5 + 3), d = Math.max(4, f.depth() * 0.5 + 3);
		double h = Math.max(8, f.height() + 5), age = f.motionAge();
		double deploy = smooth(age / 30), opening = smooth((age - 38) / 48);
		double leave = f.revealing() ? smooth(f.age() / 52) : 0;
		double alpha = 1 - leave;
		// Hinged solid wings carry emissive ribs; all surfaces are world geometry, not HUD artwork.
		for (int side : new int[] {-1, 1}) for (int wing = 0; wing < 3; wing++) {
			double z = (wing - 1) * d * 0.65;
			m.push(side * (w + leave * 12), h * 0.5 * deploy, z);
			m.rotate(0, side * (opening * 1.28 + leave * 0.65), side * (1 - deploy) * 1.25);
			m.box(0.14, h * 0.5, d * 0.27, 0x273335, Material.SOLID, alpha);
			m.push(-side * 0.17, 0, 0);
			m.box(0.035, h * 0.46, d * 0.24, 0x72928F, Material.VEIL, 0.22 * alpha);
			m.pop();
			for (int rib : new int[] {-1, 1}) {
				m.tube(-side * 0.19, -h * 0.47, rib * d * 0.23, -side * 0.19, h * 0.47, rib * d * 0.23,
					0.045, rib < 0 ? 0xE5BE72 : 0xA6F3DE, Material.GLOW, alpha * 0.9);
			}
			for (int hinge = 0; hinge < 4; hinge++) {
				m.push(0, h * (hinge / 4.0 - 0.38), -d * 0.29);
				m.rotate(0, age * 0.04, Math.PI / 2);
				m.box(0.22, 0.22, 0.28, 0xC3A369, Material.SOLID, alpha);
				m.pop();
			}
			m.pop();
		}
		// Successive rectangular portals cross the site like edits along a film timeline.
		for (int gate = 0; gate < 3; gate++) {
			double cycle = f.revealing() ? 1 : (Math.max(0, age - 72) / 90 + gate / 3.0) % 1;
			double z = mix(-d - 2, d + 2, cycle), pulse = Math.sin(cycle * Math.PI) * alpha;
			double expansion = leave * (gate + 1) * 6;
			for (int side : new int[] {-1, 1}) {
				m.tube(side * (w + expansion), 0.15, z, side * (w + expansion), h * deploy, z,
					0.13, 0x6C8380, Material.SOLID, alpha);
				m.tube(side * (w - 0.18 + expansion), 0.4, z, side * (w - 0.18 + expansion), h * deploy, z,
					0.035, 0xADFFE5, Material.GLOW, pulse);
			}
			m.tube(-w - expansion, h * deploy, z, w + expansion, h * deploy, z, 0.16, 0x526C69, Material.SOLID, alpha);
			m.tube(-w, h * deploy - 0.25, z, w, h * deploy - 0.25, z, 0.055, 0xF5DAAA, Material.GLOW, pulse);
			for (int tooth = 0; tooth < 12; tooth++) {
				m.push((tooth / 11.0 * 2 - 1) * w, h * deploy + 0.18, z);
				m.box(w / 28, 0.11, 0.24, (tooth & 1) == 0 ? 0xCDD9D5 : 0x263130, Material.SOLID, alpha);
				m.pop();
			}
		}
		double scan = f.revealing() ? f.height() + 0.2 : Math.max(0.2, f.height() * f.progress());
		m.push(0, scan, 0);
		m.rectangle(w, d, 0.04, 0xD2F5DA, 0.75 * alpha);
		m.pop();
		// Four suspended architectural crates snap open at the last setup beat.
		for (int corner = 0; corner < 4; corner++) {
			double a = corner * Math.PI / 2 + age * 0.008 * (1 - opening);
			double r = Math.min(38, Math.max(w, d) + 6);
			m.push(Math.cos(a) * r, 3.5 + Math.sin(age * 0.035 + corner) * 0.3, Math.sin(a) * r);
			m.rotate(0, a + opening * Math.PI / 2, (1 - opening) * 0.2);
			for (int side : new int[] {-1, 1}) {
				m.push(side * (1.2 + opening * 1.4 + leave * 4), 0, 0);
				m.rotate(0, side * opening * 1.4, 0);
				m.box(0.09, 1.5, 1.5, 0x3E5351, Material.SOLID, alpha);
				m.tube(-side * 0.12, -1.4, -1.3, -side * 0.12, 1.4, 1.3, 0.05, 0xF4C47D, Material.GLOW, alpha);
				m.pop();
			}
			m.pop();
		}
	}

	static double smooth(double value) { double t = Math.clamp(value, 0, 1); return t * t * (3 - 2 * t); }
	private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
