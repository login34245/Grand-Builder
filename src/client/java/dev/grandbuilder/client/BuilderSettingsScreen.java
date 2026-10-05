package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.PlacementPolicy;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BuilderSettingsScreen extends Screen {
	private final BuilderMenuScreen parent;
	private int left, top, panelWidth, panelHeight, rowHeight;
	private BuilderSettingsLayout layout;

	BuilderSettingsScreen(BuilderMenuScreen parent) {
		super(Component.translatable("screen.grand_builder.settings.title"));
		this.parent = parent;
	}
	@Override protected void init() {
		layout = BuilderSettingsLayout.at(width, height);
		panelWidth = layout.width(); panelHeight = layout.height(); left = layout.left(); top = layout.top();
		rowHeight = layout.rowHeight();
		BuilderTipPreferences prefs = BuilderTipPreferences.get();
		bool(0, prefs.enabled(), prefs::setEnabled);
		cycle(1, BuilderTheme.values(), prefs.theme(), v -> Component.translatable(v.translationKey()), prefs::setTheme);
		cycle(2, new Boolean[] { false, true }, prefs.structureList(), v -> Component.translatable(
			v ? "screen.grand_builder.settings.list" : "screen.grand_builder.settings.cycle"), prefs::setStructureList);
		cycle(3, BuildSpeed.values(), prefs.defaultSpeed(), v -> Component.translatable(v.translationKey()), prefs::setDefaultSpeed);
		bool(4, prefs.defaultTerrain(), prefs::setDefaultTerrain);
		cycle(5, BuildEffectMode.values(), prefs.defaultEffect(), v -> Component.translatable(v.translationKey()), prefs::setDefaultEffect);
		cycle(6, PlacementPolicy.values(), prefs.defaultPlacement(), v -> Component.translatable(v.translationKey()), prefs::setDefaultPlacement);
		int available = panelWidth - 24, half = (available - 4) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.settings.reset"), b -> {
			prefs.resetDefaults(); rebuildWidgets();
		}).bounds(left + 12, layout.footerY(), half, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(left + 16 + half, layout.footerY(), available - half - 4, 20).build());
	}
	private void bool(int row, boolean value, Consumer<Boolean> change) {
		cycle(row, new Boolean[] { true, false }, value, v -> Component.translatable(v ? "options.on" : "options.off"), change);
	}
	private <T> void cycle(int row, T[] values, T value, Function<T, Component> name, Consumer<T> change) {
		int size = (panelWidth - 28) / 2, x = left + panelWidth - size - 12;
		addRenderableWidget(CycleButton.<T>builder(v -> fit(name.apply(v), size - 12), value)
			.withValues(values).displayOnlyValue().withTooltip(v -> Tooltip.create(name.apply(v)))
			.create(x, layout.rowY(row), size, rowHeight, label(row), (b, v) -> change.accept(v)));
	}
	private Component label(int row) { return Component.translatable("screen.grand_builder.settings.row" + row); }
	private Component fit(Component text, int size) {
		return font.width(text) <= size ? text : Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(8, size - 12)) + "...");
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		parent.render(graphics, -1, -1, partialTick);
		graphics.fill(0, 0, width, height, 0x99000000);
		BuilderTheme theme = BuilderTheme.current();
		graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, theme.border);
		graphics.fillGradient(left, top, left + panelWidth, top + panelHeight, theme.panelTop, theme.panelBottom);
		ThemeText.centered(graphics, font, title, width / 2, top + 10, theme.text);
		for (int row = 0; row < 7; row++) ThemeText.draw(graphics, font, fit(label(row), (panelWidth - 28) / 2),
			left + 12, layout.rowY(row) + (rowHeight - 8) / 2, theme.muted);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	@Override public void onClose() { minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void resize(int width, int height) { parent.resize(width, height); super.resize(width, height); }
}
