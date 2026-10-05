package dev.grandbuilder.client;

import dev.grandbuilder.build.StructureLibrary.SelectionEntry;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class StructureSelectionScreen extends Screen {
	private final BuilderMenuScreen parent;
	private String query = "", selectedKey;
	private Rows rows;
	private Button done;
	private int revision, left, panelWidth;
	StructureSelectionScreen(BuilderMenuScreen parent, String selectedKey) {
		super(Component.translatable("screen.grand_builder.structure"));
		this.parent = parent; this.selectedKey = selectedKey;
	}
	@Override protected void init() {
		panelWidth = Math.min(400, width - 16); left = (width - panelWidth) / 2;
		EditBox search = addRenderableWidget(new EditBox(font, left + 12, 30, panelWidth - 24, 20,
			Component.translatable("screen.grand_builder.settings.search")));
		search.setHint(Component.translatable("screen.grand_builder.settings.search"));
		search.setMaxLength(128);
		search.setValue(query);
		rows = addRenderableWidget(new Rows(panelWidth - 24, Math.max(24, height - 94), 56));
		rows.setX(left + 12);
		search.setResponder(text -> { query = text; populate(); });
		int half = (panelWidth - 28) / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
			.bounds(left + 12, height - 30, half, 20).build());
		done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> choose())
			.bounds(left + 16 + half, height - 30, half, 20).build());
		populate();
	}
	private void populate() {
		String filter = query.toLowerCase(Locale.ROOT).strip();
		rows.clear();
		for (SelectionEntry entry : parent.selectionEntries()) {
			if (!entry.displayName().getString().toLowerCase(Locale.ROOT).contains(filter)) continue;
			Row row = new Row(entry); rows.add(row);
			if (entry.key().equals(selectedKey)) rows.setSelected(row);
		}
		rows.revealSelected(); done.active = rows.getSelected() != null;
		revision = StructureListClientState.revision();
	}
	private void choose() {
		if (rows.getSelected() == null) return;
		parent.selectStructure(rows.getSelected().entry.key());
		onClose();
	}
	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER && rows.getSelected() != null) { choose(); return true; }
		boolean result = super.keyPressed(event);
		if (rows.getSelected() != null) { selectedKey = rows.getSelected().entry.key(); done.active = true; }
		return result;
	}
	@Override public void tick() { if (revision != StructureListClientState.revision()) { parent.refreshSelections(); populate(); } }
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		BuilderTheme theme = BuilderTheme.current();
		graphics.fillGradient(0, 0, width, height, theme.backdropTop, theme.backdropBottom);
		graphics.fillGradient(left, 6, left + panelWidth, height - 6, theme.panelTop, theme.panelBottom);
		graphics.drawCenteredString(font, title, width / 2, 14, theme.text);
		super.render(graphics, mouseX, mouseY, partialTick);
		if (rows.children().isEmpty()) graphics.drawCenteredString(font,
			Component.translatable("screen.grand_builder.settings.no_results"), width / 2, height / 2, theme.muted);
	}
	@Override public void onClose() { minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void resize(int width, int height) { parent.resize(width, height); super.resize(width, height); }
	private final class Rows extends ObjectSelectionList<Row> {
		private Row tooltipRow;
		Rows(int w, int h, int y) { super(StructureSelectionScreen.this.minecraft, w, h, y, 24); }
		void clear() { clearEntries(); setSelected(null); }
		void add(Row row) { addEntry(row); }
		void revealSelected() { if (getSelected() != null) scrollToEntry(getSelected()); else setScrollAmount(0); }
		@Override public int getRowWidth() { return getWidth() - 12; }
		@Override protected void renderListBackground(GuiGraphics graphics) { }
		@Override protected void renderListSeparators(GuiGraphics graphics) { }
		@Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
			Row hovered = getHovered();
			if (hovered != tooltipRow) {
				tooltipRow = hovered;
				setTooltip(hovered != null && font.width(hovered.entry.displayName()) > getRowWidth() - 14
					? Tooltip.create(hovered.entry.displayName()) : null);
			}
		}
	}
	private final class Row extends ObjectSelectionList.Entry<Row> {
		private final SelectionEntry entry;
		Row(SelectionEntry entry) { this.entry = entry; }
		@Override public Component getNarration() { return entry.displayName(); }
		@Override public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			BuilderTheme theme = BuilderTheme.current();
			if (hovered) graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom(), theme.border & 0x55FFFFFF);
			String name = entry.displayName().getString(); int size = getContentWidth() - 6;
			if (font.width(name) > size) name = font.plainSubstrByWidth(name, size - 12) + "...";
			graphics.drawString(font, name, getContentX() + 3, getContentY() + 5, theme.text);
		}
		@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != 0) return false;
			rows.setSelected(this); selectedKey = entry.key(); done.active = true;
			if (doubleClick) choose();
			return true;
		}
	}
}
