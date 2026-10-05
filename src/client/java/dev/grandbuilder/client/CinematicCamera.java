package dev.grandbuilder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class CinematicCamera {
	public record Shot(Vec3 position, float yaw, float pitch, float roll, int index) { }
	private CinematicCamera() { }

	public static Shot pose(GrandBuilderClientEffects.Frame f, int width, int height, double fov) {
		double extent = Math.max(f.width(), f.depth()), age = f.motionAge();
		int shot = f.revealing() ? 3 : age < 42 ? 0 : age < 96 ? 1 : 2;
		double beat = SetChangeGeometry.smooth(shot == 0 ? age / 42 : shot == 1 ? (age - 42) / 54
			: shot == 3 ? f.age() / 80 : f.progress());
		double angle = switch (shot) { case 0 -> -0.82 + beat * 0.2; case 1 -> 0.7 + beat * 0.45;
			case 2 -> -0.65 + beat * 0.65; default -> 0.65 + beat * 0.25; };
		double elevation = switch (shot) { case 0 -> 0.20; case 1 -> 0.95; case 2 -> 0.36; default -> 0.25; };
		double aspect = Math.max(0.3, width / (double) Math.max(1, height * 5 / 6));
		double tangent = Math.tan(Math.toRadians(Math.clamp(fov, 30, 110)) / 2);
		// Fit the complete bounding sphere, including rig clearance, at both portrait and wide aspects.
		double radius = Math.sqrt(extent * extent * 2 + f.height() * f.height()) * 0.5 + 8;
		double distance = radius / Math.sin(Math.atan(tangent * Math.min(1, aspect))) * (shot == 3 ? 1.12 + beat * 0.12 : 1.05);
		Vec3 target = new Vec3(f.x(), f.y() + f.height() * 0.48, f.z());
		Vec3 eye = target.add(Math.sin(angle) * Math.cos(elevation) * distance,
			Math.sin(elevation) * distance, Math.cos(angle) * Math.cos(elevation) * distance);
		Vec3 delta = target.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
		return new Shot(eye, yaw, pitch, (float) (Math.sin(beat * Math.PI) * (shot == 1 ? -0.025 : 0.012)), shot);
	}

	public static Shot current(float partialTick) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.screen != null || !BuilderTipPreferences.get().cinematicCamera()) return null;
		var f = GrandBuilderClientEffects.cinematicFrame(partialTick, client.player.getUUID());
		if (f == null) return null;
		Shot shot = pose(f, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.options.fov().get());
		// Rendering a shot must never request chunks, move the player or place a camera in an unloaded area.
		if (shot.position().distanceToSqr(client.player.position()) > 192 * 192
			|| !client.level.hasChunkAt(net.minecraft.core.BlockPos.containing(shot.position()))
			|| !client.level.getBlockState(net.minecraft.core.BlockPos.containing(shot.position())).isAir()) return null;
		return shot;
	}

	public static void renderOverlay(net.minecraft.client.gui.GuiGraphics graphics, float partialTick) {
		Minecraft client = Minecraft.getInstance();
		if (current(partialTick) == null) return;
		int bar = Math.max(8, graphics.guiHeight() / 12);
		graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF080B0D);
		graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF080B0D);
		var frame = GrandBuilderClientEffects.cinematicFrame(partialTick, client.player.getUUID());
		if (frame != null && frame.motionAge() < 80) {
			var label = net.minecraft.network.chat.Component.translatable("screen.grand_builder.cinematic_skip", dev.grandbuilder.GrandBuilderModClient.cinematicKeyName());
			graphics.drawString(client.font, label, 8, graphics.guiHeight() - bar + (bar - 8) / 2, 0xFFB7C4CA, false);
		}
	}
}
