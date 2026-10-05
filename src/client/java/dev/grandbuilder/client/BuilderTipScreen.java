package dev.grandbuilder.client;

import dev.grandbuilder.GrandBuilderModClient;
import dev.grandbuilder.network.BuildControlAction;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class BuilderTipScreen extends Screen {
	private final BuilderMenuScreen parent;
	private int tip;
	private int left, top, panelWidth, panelHeight, contentWidth;
	private List<FormattedCharSequence> lines;

	BuilderTipScreen(BuilderMenuScreen parent, int tip) {
		super(Component.translatable("screen.grand_builder.tips.title"));
		this.parent = parent;
		this.tip = tip;
	}

	@Override protected void init() {
		panelWidth = Math.min(320, width - 16);
		contentWidth = panelWidth - 24;
		lines = font.split(tipMessage(), contentWidth);
		panelHeight = Math.min(height - 16, 90 + lines.size() * 11);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		addRenderableWidget(Checkbox.builder(Component.translatable("screen.grand_builder.tips.enabled")
			.withStyle(style -> style.withColor(BuilderTheme.current().text & 0xFFFFFF)), font)
			.pos(left + 12, top + panelHeight - 54).maxWidth(contentWidth)
			.selected(BuilderTipPreferences.get().enabled())
			.onValueChange((checkbox, enabled) -> BuilderTipPreferences.get().setEnabled(enabled)).build());
		int buttonWidth = (contentWidth - 4) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.tips.next"), button -> {
			tip = BuilderTipPreferences.get().takeNextTip();
			rebuildWidgets();
		}).bounds(left + 12, top + panelHeight - 28, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(left + 16 + buttonWidth, top + panelHeight - 28, contentWidth - buttonWidth - 4, 20).build());
	}

	private Component tipMessage() {
		String key = "screen.grand_builder.tips.tip" + tip;
		return switch (tip) {
			case 0 -> Component.translatable(key, GrandBuilderModClient.fastPreviewKeyName());
			case 1 -> Component.translatable(key, GrandBuilderModClient.previewKeyName(BuildControlAction.ROTATE_PREVIEW));
			case 2 -> Component.translatable(key, GrandBuilderModClient.previewKeyName(BuildControlAction.MOVE_PREVIEW_UP),
				GrandBuilderModClient.previewKeyName(BuildControlAction.MOVE_PREVIEW_DOWN));
			default -> Component.translatable(key);
		};
	}

	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		parent.render(graphics, -1, -1, partialTick);
		graphics.fill(0, 0, width, height, 0xB0000000);
		BuilderTheme theme = BuilderTheme.current();
		graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, theme.border);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, theme.panelTop);
		ThemeText.draw(graphics, font, title, left + 12, top + 12, theme.accent);
		int y = top + 30;
		graphics.enableScissor(left + 12, y, left + 12 + contentWidth, top + panelHeight - 60);
		for (FormattedCharSequence line : lines) {
			ThemeText.draw(graphics, font, line, left + 12, y, theme.text);
			y += 11;
		}
		graphics.disableScissor();
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override public void onClose() { minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void resize(int width, int height) {
		parent.resize(width, height);
		super.resize(width, height);
	}
}
