package dev.grandbuilder.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class SpaceSceneProjection {
	public record Face(float[] xy, int[] colors, double distance) { }
	private SpaceSceneProjection() { }
	public static List<Face> project(double age, int width, int height) {
		EuropaGeometry.View view = EuropaGeometry.view(age);
		Vec3 forward = view.target().subtract(view.eye()).normalize();
		Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(forward).normalize();
		double focal = Math.min(width, height * 1.55) * 0.77;
		List<Face> faces = new ArrayList<>(3200);
		float[] xy = new float[8]; int[] colors = new int[4];
		double[] depths = new double[4]; int[] count = {0};
		EuropaGeometry.space(age, (material, x, y, z, color) -> {
			int i = count[0]++ & 3;
			Vec3 p = new Vec3(x, y, z).subtract(view.eye());
			double depth = p.dot(forward); depths[i] = depth;
			xy[i * 2] = (float) (width * 0.5 + p.dot(right) * focal / Math.max(0.2, depth));
			xy[i * 2 + 1] = (float) (height * 0.48 - p.dot(up) * focal / Math.max(0.2, depth));
			colors[i] = color;
			if (i != 3) return;
			for (double d : depths) if (d <= 0.2) return;
			boolean visible = false;
			for (int j = 0; j < 4; j++) if (xy[j * 2] >= -width && xy[j * 2] <= width * 2
				&& xy[j * 2 + 1] >= -height && xy[j * 2 + 1] <= height * 2) visible = true;
			if (visible && (color >>> 24) > 0) {
				float[] vertices = xy.clone(); int[] shades = colors.clone();
				// Match native GUI rectangle winding after perspective projection.
				if (signedArea(vertices) > 0) {
					for (int axis = 0; axis < 2; axis++) { float a = vertices[2 + axis]; vertices[2 + axis] = vertices[6 + axis]; vertices[6 + axis] = a; }
					int a = shades[1]; shades[1] = shades[3]; shades[3] = a;
				}
				faces.add(new Face(vertices, shades, (depths[0] + depths[1] + depths[2] + depths[3]) / 4));
			}
		});
		// Sort the self-contained space set independently of world depth, fog and shader targets.
		faces.sort(Comparator.comparingDouble(Face::distance).reversed());
		return List.copyOf(faces);
	}
	static double signedArea(float[] xy) {
		double area = 0;
		for (int i = 0; i < 4; i++) { int j = (i + 1) & 3; area += (double) xy[i * 2] * xy[j * 2 + 1] - (double) xy[j * 2] * xy[i * 2 + 1]; }
		return area;
	}
}
