package dev.grandbuilder.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.EuropaTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class EuropaCinematic {
	private static final Identifier PORTRAIT = Identifier.fromNamespaceAndPath("grand_builder", "textures/cinematic/europa_captain.png");
	private static final List<SoundInstance> SOUNDS = new ArrayList<>();
	private static UUID soundScene;
	private static double lastSoundAge;
	private static int played;
	private EuropaCinematic() { }
	public static void tick(Minecraft c) {
		if (c.player == null || c.level == null) { stopSounds(); soundScene = null; return; }
		if (c.isPaused()) return;
		var f = GrandBuilderClientEffects.cinematicFrame(0, c.player.getUUID());
		if (f == null || f.mode() != BuildEffectMode.EUROPA_CALL || c.screen != null || !BuilderTipPreferences.get().cinematicCamera()
			|| f.revealing() && f.age() >= EuropaTimeline.FLASH) { stopSounds(); return; }
		UUID id = GrandBuilderClientEffects.cinematicSceneId(c.player.getUUID());
		double age = f.revealing() ? EuropaTimeline.ARRIVAL + f.age() : f.age();
		if (!id.equals(soundScene)) { stopSounds(); soundScene = id; played = 0; lastSoundAge = Math.max(-1, age - 1); }
		cue(c, age, 0, 1, GrandBuilderMod.EUROPA_RADIO, 0.65f);
		cue(c, age, 1, EuropaTimeline.JUPITER, GrandBuilderMod.EUROPA_RELAY, 0.65f);
		cue(c, age, 2, EuropaTimeline.EUROPA, GrandBuilderMod.EUROPA_RADIO, 0.55f);
		cue(c, age, 3, EuropaTimeline.STATION, GrandBuilderMod.EUROPA_CHARGE, 0.7f);
		cue(c, age, 4, EuropaTimeline.FIRE, GrandBuilderMod.EUROPA_BEAM, 0.75f);
		if (f.revealing() && (played & 1 << 5) == 0) {
			lastSoundAge = Math.min(lastSoundAge, EuropaTimeline.ARRIVAL - 1);
			cue(c, age, 5, EuropaTimeline.ARRIVAL, GrandBuilderMod.EUROPA_FLASH, 0.65f);
		}
		lastSoundAge = age;
	}
	private static void cue(Minecraft c, double age, int bit, int tick, SoundEvent event, float volume) {
		if ((played & 1 << bit) != 0 || age < tick || lastSoundAge > tick) return;
		played |= 1 << bit;
		SoundInstance sound = SimpleSoundInstance.forUI(event, 1, volume);
		SOUNDS.add(sound); c.getSoundManager().play(sound);
	}
	public static void stopSounds() {
		if (SOUNDS.isEmpty()) return;
		SOUNDS.forEach(Minecraft.getInstance().getSoundManager()::stop); SOUNDS.clear();
	}
	public static void render(GuiGraphics g, GrandBuilderClientEffects.Frame f) {
		Minecraft c = Minecraft.getInstance();
		int w = g.guiWidth(), h = g.guiHeight(), bar = Math.max(10, h / 12);
		if (f.revealing()) {
			int alpha = (int) Math.round(EuropaTimeline.flashAlpha(f.age()) * 255);
			g.fill(0, 0, w, h, alpha << 24 | 0xFFFFFF);
			return;
		}
		int shot = EuropaTimeline.shot(f.age());
		if (shot > 0) {
			g.fill(0, 0, w, h, 0xFF040609);
			for (int i = 0; i < 170; i++) {
				int x = Math.floorMod(i * 137 + 31, w), y = Math.floorMod(i * 223 + 47, h);
				int alpha = (int) (85 + 50 * Math.sin(i + f.age() * 0.03));
				g.fill(x, y, x + (i % 19 == 0 ? 2 : 1), y + 1, alpha << 24 | 0xC6D7DF);
			}
			g.guiRenderState.submitGuiElement(new SpaceState(SpaceSceneProjection.project(f.age(), w, h),
				new Matrix3x2f(g.pose()), new ScreenRectangle(0, bar, w, h - bar * 2)));
		}
		g.fill(0, 0, w, bar, 0xFF06090C); g.fill(0, h - bar, w, h, 0xFF06090C);
		String heading = "screen.grand_builder.europa." + switch (shot) {
			case 0 -> "calling"; case 1 -> "jupiter"; case 2 -> "europa"; case 3 -> "station"; default -> "target";
		};
		label(g, Component.translatable(heading), 8, Math.max(1, (bar - 8) / 2), w - 16, 0xFFE0E8E8);
		if (shot == 0) terminal(g, f, false, bar);
		if (shot == 2) terminal(g, f, true, bar);
		if (shot == 3) {
			int start = w / 4, end = w * 3 / 4, y = h - bar - 14;
			g.fill(start, y, end, y + 2, 0xFF34443B);
			g.fill(start, y, start + (int) ((end - start) * EuropaGeometry.smooth((f.age() - 225) / 48)), y + 2, 0xFFABFFC3);
		}
		label(g, Component.translatable("screen.grand_builder.cinematic_skip", dev.grandbuilder.GrandBuilderModClient.cinematicKeyName()),
			8, h - bar + Math.max(1, (bar - 8) / 2), w - 16, 0xFF93A4AB);
	}
	private static void terminal(GuiGraphics g, GrandBuilderClientEffects.Frame f, boolean reply, int bar) {
		Minecraft c = Minecraft.getInstance(); int w = g.guiWidth(), h = g.guiHeight();
		int panelWidth = Math.min(320, w - 24), panelHeight = Math.min(reply ? 106 : 76, h - bar * 2 - 12);
		int left = (w - panelWidth) / 2, top = reply ? h - bar - panelHeight - 8 : (h - panelHeight) / 2;
		g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF65756C);
		g.fill(left, top, left + panelWidth, top + panelHeight, 0xF0101C20);
		int portraitSize = reply ? Math.min(96, panelHeight - 8) : 0;
		if (reply) {
			g.enableScissor(left + 4, top + 4, left + 4 + portraitSize, top + panelHeight - 4);
			g.blit(RenderPipelines.GUI_TEXTURED, PORTRAIT, left + 4, top + 4, 0, 0, portraitSize, portraitSize, 512, 512, 512, 512);
			for (int y = top + 4; y < top + panelHeight - 4; y += 3) g.fill(left + 4, y, left + 4 + portraitSize, y + 1, 0x28101C20);
			g.disableScissor();
		}
		int textX = left + (reply ? portraitSize + 12 : 10), textWidth = left + panelWidth - textX - 10;
		label(g, Component.translatable("screen.grand_builder.europa." + (reply ? "captain" : "uplink")), textX, top + 9, textWidth, 0xFFB2DBC8);
		String key = reply ? f.age() < 166 ? "reply1" : "reply2" : f.age() < 28 ? "request1" : "request2";
		List<net.minecraft.util.FormattedCharSequence> lines = c.font.split(Component.translatable("screen.grand_builder.europa." + key), textWidth);
		int y = top + 25;
		for (var line : lines) { if (y + 8 > top + panelHeight - 18) break; g.drawString(c.font, line, textX, y, 0xFFE4EAE5, false); y += c.font.lineHeight; }
		for (int i = 0; i < textWidth / 4; i++) {
			int size = 1 + (int) (Math.abs(Math.sin(f.age() * 0.38 + i * 1.4) * Math.cos(i * 0.6)) * 8);
			g.fill(textX + i * 4, top + panelHeight - 9 - size / 2, textX + i * 4 + 2, top + panelHeight - 8 + size / 2, 0xFF7CAA97);
		}
	}
	private static void label(GuiGraphics g, Component text, int x, int y, int size, int color) {
		Minecraft c = Minecraft.getInstance(); String s = text.getString();
		if (c.font.width(s) > size) s = c.font.plainSubstrByWidth(s, Math.max(1, size - 12)) + "...";
		g.drawString(c.font, s, x, y, color, false);
	}
	private record SpaceState(List<SpaceSceneProjection.Face> faces, Matrix3x2fc pose, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
		@Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
		@Override public ScreenRectangle scissorArea() { return bounds; }
		@Override public void buildVertices(VertexConsumer v) {
			for (var face : faces) for (int i = 0; i < 4; i++) v.addVertexWith2DPose(pose, face.xy()[i * 2], face.xy()[i * 2 + 1]).setColor(face.colors()[i]);
		}
	}
}
