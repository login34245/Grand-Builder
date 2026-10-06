package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class EffectSelectionScreen extends Screen {
	private final BuilderMenuScreen parent;
	private BuildEffectMode selected;
	private String query = "";
	private Rows rows;
	private Button done;
	private int left, panelWidth;
	EffectSelectionScreen(BuilderMenuScreen parent, BuildEffectMode selected) {
		super(Component.translatable("screen.grand_builder.effects"));
		this.parent = parent; this.selected = selected;
	}
	@Override protected void init() {
		panelWidth = Math.min(400, width - 16); left = (width - panelWidth) / 2;
		EditBox search = addRenderableWidget(new EditBox(font, left + 12, 30, panelWidth - 24, 20,
			Component.translatable("screen.grand_builder.effects_search")));
		search.setHint(Component.translatable("screen.grand_builder.effects_search"));
		search.setMaxLength(128); search.setValue(query);
		rows = addRenderableWidget(new Rows(panelWidth - 24, Math.max(24, height - 94), 56));
		rows.setX(left + 12);
		search.setResponder(value -> { query = value; populate(); });
		int half = (panelWidth - 28) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
			.bounds(left + 12, height - 30, half, 20).build());
		done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> choose())
			.bounds(left + 16 + half, height - 30, half, 20).build());
		populate();
	}
	private void populate() {
		rows.clear();
		String filter = query.strip().toLowerCase(Locale.ROOT);
		for (BuildEffectMode mode : BuildEffectMode.values()) {
			if (!Component.translatable(mode.translationKey()).getString().toLowerCase(Locale.ROOT).contains(filter)) continue;
			Row row = new Row(mode); rows.add(row);
			if (mode == selected) rows.setSelected(row);
		}
		rows.reveal(); done.active = rows.getSelected() != null;
	}
	private void choose() {
		if (rows.getSelected() == null) return;
		parent.selectEffect(rows.getSelected().mode); onClose();
	}
	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER && rows.getSelected() != null) { choose(); return true; }
		boolean handled = super.keyPressed(event);
		if (rows.getSelected() != null) { selected = rows.getSelected().mode; done.active = true; }
		return handled;
	}
	@Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		BuilderTheme theme = BuilderTheme.current();
		g.fillGradient(0, 0, width, height, theme.backdropTop, theme.backdropBottom);
		g.fillGradient(left, 6, left + panelWidth, height - 6, theme.panelTop, theme.panelBottom);
		ThemeText.centered(g, font, title, width / 2, 14, theme.text);
		super.render(g, mouseX, mouseY, partial);
		if (rows.children().isEmpty()) ThemeText.centered(g, font, Component.translatable("screen.grand_builder.effects_empty"),
			width / 2, height / 2, theme.muted);
	}
	@Override public void onClose() { minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void resize(int w, int h) { parent.resize(w, h); super.resize(w, h); }
	private final class Rows extends ObjectSelectionList<Row> {
		Rows(int w, int h, int y) { super(EffectSelectionScreen.this.minecraft, w, h, y, 24); }
		void clear() { clearEntries(); setSelected(null); }
		void add(Row row) { addEntry(row); }
		void reveal() { if (getSelected() != null) scrollToEntry(getSelected()); else setScrollAmount(0); }
		@Override public int getRowWidth() { return getWidth() - 12; }
		@Override protected void renderListBackground(GuiGraphics g) { }
		@Override protected void renderListSeparators(GuiGraphics g) { }
		@Override protected void renderSelection(GuiGraphics g, Row row, int color) {
			if (BuilderTheme.current() != BuilderTheme.LIGHT) { super.renderSelection(g, row, color); return; }
			g.fill(row.getX(), row.getY(), row.getX() + row.getWidth(), row.getY() + row.getHeight(), 0xFF486B64);
			g.fill(row.getX() + 1, row.getY() + 1, row.getX() + row.getWidth() - 1, row.getY() + row.getHeight() - 1, 0xFFD2E2DC);
		}
	}
	private final class Row extends ObjectSelectionList.Entry<Row> {
		private final BuildEffectMode mode;
		Row(BuildEffectMode mode) { this.mode = mode; }
		@Override public Component getNarration() { return Component.translatable(mode.translationKey()); }
		@Override public void renderContent(GuiGraphics g, int x, int y, boolean hovered, float partial) {
			BuilderTheme theme = BuilderTheme.current();
			String name = getNarration().getString(); int space = getContentWidth() - 6;
			if (font.width(name) > space) {
				if (hovered) g.setTooltipForNextFrame(getNarration(), x, y);
				name = font.plainSubstrByWidth(name, Math.max(1, space - 12)) + "...";
			}
			ThemeText.draw(g, font, name, getContentX() + 3, getContentY() + 5, theme.text);
		}
		@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != 0) return false;
			rows.setSelected(this); selected = mode; done.active = true;
			if (doubleClick) choose(); return true;
		}
	}
}
