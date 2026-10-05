package dev.grandbuilder.client;

import dev.grandbuilder.network.FilmingAction;
import dev.grandbuilder.network.FilmingStatePayload;
import dev.grandbuilder.network.FilmingToolsPayload;
import java.util.EnumMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FilmingToolsScreen extends Screen {
	private final BuilderMenuScreen parent;
	private final EnumMap<FilmingAction, Button> actions = new EnumMap<>(FilmingAction.class);
	private FilmingStatePayload state;
	private int left, top, panelWidth, panelHeight, buttonHeight, invisibleY, timeY, weatherY;
	private int pollTicks, pendingTicks, feedbackTicks;
	private String feedbackKey;

	FilmingToolsScreen(BuilderMenuScreen parent) {
		super(Component.translatable("screen.grand_builder.filming.title"));
		this.parent = parent;
	}

	@Override protected void init() {
		actions.clear();
		panelWidth = Math.min(254, width - 16);
		buttonHeight = height < 220 ? 14 : 20;
		panelHeight = 96 + buttonHeight * 5;
		left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
		invisibleY = top + 37;
		timeY = invisibleY + buttonHeight + 18;
		weatherY = timeY + (buttonHeight + 3) * 2 + 15;
		addRow(invisibleY, FilmingAction.INVISIBILITY_ON, FilmingAction.INVISIBILITY_OFF);
		addRow(timeY, FilmingAction.DAWN, FilmingAction.DAY);
		addRow(timeY + buttonHeight + 3, FilmingAction.EVENING, FilmingAction.NIGHT);
		addRow(weatherY, FilmingAction.CLEAR, FilmingAction.RAIN, FilmingAction.THUNDER);
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
			.bounds(left + 12, top + panelHeight - buttonHeight - 10, panelWidth - 24, buttonHeight).build());
		request(FilmingAction.STATUS);
		refresh();
	}

	private void addRow(int y, FilmingAction... row) {
		int available = panelWidth - 24;
		int size = (available - (row.length - 1) * 4) / row.length;
		for (int i = 0; i < row.length; i++) {
			FilmingAction action = row[i];
			Component full = Component.translatable(action.translationKey());
			String label = full.getString();
			if (font.width(label) > size - 8) label = font.plainSubstrByWidth(label, size - 14) + "...";
			Button button = addRenderableWidget(Button.builder(Component.literal(label), widget -> request(action))
				.bounds(left + 12 + i * (size + 4), y, size, buttonHeight).build());
			button.setTooltip(Tooltip.create(action == FilmingAction.INVISIBILITY_ON
				? Component.translatable("screen.grand_builder.filming.invisibility_tooltip") : full));
			actions.put(action, button);
		}
	}

	private void request(FilmingAction action) {
		ClientPlayNetworking.send(new FilmingToolsPayload(action.ordinal()));
		pollTicks = 20;
		if (action != FilmingAction.STATUS) { pendingTicks = 80; refresh(); }
	}

	public void receiveState(FilmingStatePayload payload) {
		state = payload;
		if (payload.result() != FilmingStatePayload.STATUS) {
			pendingTicks = 0;
			feedbackKey = switch (payload.result()) {
				case FilmingStatePayload.APPLIED -> "screen.grand_builder.filming.applied";
				case FilmingStatePayload.COOLDOWN -> "message.grand_builder.cooldown";
				default -> "screen.grand_builder.filming.denied";
			};
			feedbackTicks = 50;
		}
		refresh();
	}

	private void refresh() {
		for (var entry : actions.entrySet()) {
			entry.getValue().active = state != null && state.permitted() && pendingTicks == 0;
			if (entry.getKey() == FilmingAction.INVISIBILITY_OFF && state != null && !state.invisible()) entry.getValue().active = false;
		}
	}

	@Override public void tick() {
		if (pendingTicks > 0 && --pendingTicks == 0) {
			feedbackKey = "screen.grand_builder.filming.timeout"; feedbackTicks = 60; refresh();
		}
		if (feedbackTicks > 0) feedbackTicks--;
		if (--pollTicks <= 0 && pendingTicks == 0) request(FilmingAction.STATUS);
	}

	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		parent.render(graphics, -1, -1, partialTick);
		graphics.fill(0, 0, width, height, 0xB0000000);
		BuilderTheme theme = BuilderTheme.current();
		graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, theme.border);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, theme.panelTop);
		ThemeText.centered(graphics, font, title, width / 2, top + 9, theme.accent);
		String info = feedbackTicks > 0 ? feedbackKey : state == null ? "screen.grand_builder.filming.loading"
			: !state.permitted() ? "screen.grand_builder.filming.denied" : state.invisible()
			? "screen.grand_builder.filming.invisible" : "screen.grand_builder.filming.visible";
		String line = font.plainSubstrByWidth(Component.translatable(info).getString(), panelWidth - 24);
		ThemeText.centered(graphics, font, line, width / 2, top + 22, theme.muted);
		ThemeText.draw(graphics, font, Component.translatable("screen.grand_builder.filming.time"), left + 12, timeY - 11, theme.text);
		ThemeText.draw(graphics, font, Component.translatable("screen.grand_builder.filming.weather"), left + 12, weatherY - 11, theme.text);
		super.render(graphics, mouseX, mouseY, partialTick);
		if (state != null && state.permitted()) {
			FilmingAction time = state.dayTime() < 3000 ? FilmingAction.DAWN : state.dayTime() < 10000 ? FilmingAction.DAY
				: state.dayTime() < 14000 ? FilmingAction.EVENING : FilmingAction.NIGHT;
			mark(graphics, time);
			mark(graphics, state.weatherId() == 2 ? FilmingAction.THUNDER : state.weatherId() == 1 ? FilmingAction.RAIN : FilmingAction.CLEAR);
			if (state.invisible()) mark(graphics, FilmingAction.INVISIBILITY_ON);
		}
	}

	private void mark(GuiGraphics graphics, FilmingAction action) {
		Button button = actions.get(action);
		graphics.fill(button.getX() + 2, button.getBottom() - 2, button.getRight() - 2, button.getBottom() - 1, BuilderTheme.current().accent);
	}

	@Override public void onClose() { minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void resize(int width, int height) { parent.resize(width, height); super.resize(width, height); }
}
