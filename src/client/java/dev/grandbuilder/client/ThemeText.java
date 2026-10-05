package dev.grandbuilder.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ThemeText {
	private ThemeText() { }
	static void draw(GuiGraphics g, Font font, Component text, int x, int y, int color) {
		g.drawString(font, text, x, y, color, BuilderTheme.current().textShadow());
	}
	static void draw(GuiGraphics g, Font font, String text, int x, int y, int color) {
		g.drawString(font, text, x, y, color, BuilderTheme.current().textShadow());
	}
	static void draw(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
		g.drawString(font, text, x, y, color, BuilderTheme.current().textShadow());
	}
	static void centered(GuiGraphics g, Font font, Component text, int x, int y, int color) { draw(g, font, text, x - font.width(text) / 2, y, color); }
	static void centered(GuiGraphics g, Font font, String text, int x, int y, int color) { draw(g, font, text, x - font.width(text) / 2, y, color); }
}
