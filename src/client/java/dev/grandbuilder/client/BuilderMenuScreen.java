package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.CustomCaptureFormat;
import dev.grandbuilder.build.StructureLibrary;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.network.BuildRequestPayload;
import dev.grandbuilder.network.BuildSetSpeedPayload;
import dev.grandbuilder.network.CaptureRequestPayload;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class BuilderMenuScreen extends Screen {
	private static final int PANEL_WIDTH = 346;
	private static final int PANEL_HEIGHT = 316;
	private static final int MIN_PANEL_WIDTH = 220;
	private static final int MIN_PANEL_HEIGHT = 190;
	private static final int SCREEN_MARGIN = 6;
	private static final int YOUTUBE_BADGE_SIZE = 18;
	private static final int LANGUAGE_BUTTON_WIDTH = 64;
	private static final String ENGLISH_LANGUAGE = "en_us";
	private static final String RUSSIAN_LANGUAGE = "ru_ru";
	private static final int YOUTUBE_TOOLTIP_MAX_WIDTH = 220;
	private static final int LANGUAGE_TOOLTIP_MAX_WIDTH = 180;
	private static final int STRUCTURES_TOOLTIP_MAX_WIDTH = 260;

	private static String lastStructureKey = StructureLibrary.defaultSelectionEntry().key();
	private static BuildSpeed lastSpeed = BuildSpeed.NORMAL;
	private static CustomCaptureFormat lastCaptureFormat = CustomCaptureFormat.SCHEM_SINGLE;

	private List<StructureLibrary.SelectionEntry> structureChoices = new ArrayList<>();
	private int selectedStructureIndex;
	private BuildSpeed selectedSpeed = lastSpeed;
	private CustomCaptureFormat selectedCaptureFormat = lastCaptureFormat;
	private Button structureButton;
	private Button folderButton;
	private Button speedButton;
	private Button terrainButton;
	private Button captureFormatButton;
	private Button startButton;
	private Button captureButton;
	private Button pauseResumeButton;
	private Button rollbackButton;
	private Button cancelPreviewButton;
	private Button youtubeButton;
	private Button languageButton;
	private boolean terrainEnabled = true;
	private int statusPollCooldown = 0;
	private int knownStructureListRevision = -1;
	private int youtubeBadgeLeft;
	private int youtubeBadgeTop;

	private record UiLayout(
		int left,
		int top,
		int panelWidth,
		int panelHeight,
		int innerLeft,
		int innerRight,
		int contentWidth,
		int buttonHeight,
		int titleY,
		int subtitleY,
		int topSeparatorY,
		int structureLabelY,
		int structureButtonY,
		int speedLabelY,
		int speedButtonY,
		int captureLabelY,
		int captureButtonY,
		int actionsButtonY,
		int pauseButtonY,
		int cancelButtonY,
		int hintY,
		int statusSeparatorY,
		int statusTitleY,
		int statusModeY,
		int statusStructureY,
		int statusProgressY,
		int statusEtaY,
		int statusTerrainY,
		int structureButtonWidth,
		int folderButtonWidth,
		int splitLeft,
		int splitRight,
		int halfWidth,
		int languageButtonLeft,
		int languageButtonTop,
		int languageButtonWidth,
		int youtubeButtonLeft,
		int youtubeButtonTop,
		boolean showSubtitle,
		boolean showStatusStructure,
		boolean showStatusEta,
		boolean showStatusTerrain
	) {
	}

	public BuilderMenuScreen() {
		super(Component.translatable("screen.grand_builder.title"));
	}

	@Override
	protected void init() {
		PreviewConfirmState.disarm();
		sendControl(BuildControlAction.REQUEST_STRUCTURE_LIST);
		reloadChoices();

		UiLayout layout = layout();
		int actionRightWidth = layout.contentWidth() - layout.halfWidth() - 4;

		this.structureButton = this.addRenderableWidget(Button.builder(fitButtonMessage(structureMessage(), layout.structureButtonWidth()), button -> {
			this.selectedStructureIndex = (this.selectedStructureIndex + 1) % this.structureChoices.size();
			setFittedMessage(this.structureButton, structureMessage());
		}).bounds(layout.innerLeft(), layout.structureButtonY(), layout.structureButtonWidth(), layout.buttonHeight()).build());
		this.folderButton = this.addRenderableWidget(Button.builder(
			fitButtonMessage(Component.translatable("screen.grand_builder.open_structures"), layout.folderButtonWidth()),
			button -> openStructuresFolder()
		).bounds(layout.innerLeft() + layout.structureButtonWidth() + 4, layout.structureButtonY(), layout.folderButtonWidth(), layout.buttonHeight()).build());

		this.speedButton = this.addRenderableWidget(Button.builder(fitButtonMessage(speedMessage(), layout.splitLeft()), button -> {
			this.selectedSpeed = this.selectedSpeed.next();
			setFittedMessage(this.speedButton, speedMessage());
			ClientPlayNetworking.send(new BuildSetSpeedPayload(this.selectedSpeed.networkId()));
		}).bounds(layout.innerLeft(), layout.speedButtonY(), layout.splitLeft(), layout.buttonHeight()).build());
		this.terrainButton = this.addRenderableWidget(Button.builder(fitButtonMessage(terrainMessage(), layout.splitRight()), button -> {
			this.terrainEnabled = !this.terrainEnabled;
			setFittedMessage(this.terrainButton, terrainMessage());
			sendControl(BuildControlAction.TOGGLE_TERRAIN);
		}).bounds(layout.innerLeft() + layout.splitLeft() + 4, layout.speedButtonY(), layout.splitRight(), layout.buttonHeight()).build());

		this.captureFormatButton = this.addRenderableWidget(Button.builder(fitButtonMessage(captureFormatMessage(), layout.contentWidth()), button -> {
			this.selectedCaptureFormat = this.selectedCaptureFormat.next();
			lastCaptureFormat = this.selectedCaptureFormat;
			setFittedMessage(this.captureFormatButton, captureFormatMessage());
		}).bounds(layout.innerLeft(), layout.captureButtonY(), layout.contentWidth(), layout.buttonHeight()).build());

		this.startButton = this.addRenderableWidget(Button.builder(fitButtonMessage(Component.translatable("screen.grand_builder.start"), layout.halfWidth()), button -> startBuild())
			.bounds(layout.innerLeft(), layout.actionsButtonY(), layout.halfWidth(), layout.buttonHeight()).build());
		this.captureButton = this.addRenderableWidget(Button.builder(fitButtonMessage(Component.translatable("screen.grand_builder.capture"), actionRightWidth), button -> captureCustom())
			.bounds(layout.innerLeft() + layout.halfWidth() + 4, layout.actionsButtonY(), actionRightWidth, layout.buttonHeight()).build());

		this.pauseResumeButton = this.addRenderableWidget(Button.builder(fitButtonMessage(Component.translatable("screen.grand_builder.pause_resume"), layout.halfWidth()), button -> sendControl(BuildControlAction.TOGGLE_PAUSE))
			.bounds(layout.innerLeft(), layout.pauseButtonY(), layout.halfWidth(), layout.buttonHeight()).build());
		this.rollbackButton = this.addRenderableWidget(Button.builder(fitButtonMessage(Component.translatable("screen.grand_builder.rollback"), actionRightWidth), button -> sendControl(BuildControlAction.ROLLBACK))
			.bounds(layout.innerLeft() + layout.halfWidth() + 4, layout.pauseButtonY(), actionRightWidth, layout.buttonHeight()).build());
		this.cancelPreviewButton = this.addRenderableWidget(Button.builder(fitButtonMessage(Component.translatable("screen.grand_builder.cancel_preview"), layout.contentWidth()), button -> {
			sendControl(BuildControlAction.CANCEL_PREVIEW);
			PreviewConfirmState.disarm();
		}).bounds(layout.innerLeft(), layout.cancelButtonY(), layout.contentWidth(), layout.buttonHeight()).build());
		this.youtubeBadgeLeft = layout.youtubeButtonLeft();
		this.youtubeBadgeTop = layout.youtubeButtonTop();
		this.youtubeButton = this.addRenderableWidget(Button.builder(
			Component.translatable("screen.grand_builder.youtube.badge"),
			button -> openYoutubeChannel()
		).bounds(youtubeBadgeLeft, youtubeBadgeTop, YOUTUBE_BADGE_SIZE, YOUTUBE_BADGE_SIZE).build());
		this.languageButton = this.addRenderableWidget(Button.builder(
			fitButtonMessage(languageMessage(), layout.languageButtonWidth()),
			button -> switchLanguage()
		).bounds(layout.languageButtonLeft(), layout.languageButtonTop(), layout.languageButtonWidth(), YOUTUBE_BADGE_SIZE).build());

		sendControl(BuildControlAction.STATUS_SILENT);
		this.statusPollCooldown = 10;
		YoutubeChannelFeed.forceRefresh();
	}

	private UiLayout layout() {
		int maxPanelWidth = Math.max(120, this.width - SCREEN_MARGIN * 2);
		int maxPanelHeight = Math.max(120, this.height - SCREEN_MARGIN * 2);
		int panelWidth = Math.min(PANEL_WIDTH, maxPanelWidth);
		int panelHeight = Math.min(PANEL_HEIGHT, maxPanelHeight);
		if (maxPanelWidth >= MIN_PANEL_WIDTH) {
			panelWidth = Math.max(panelWidth, MIN_PANEL_WIDTH);
		}
		if (maxPanelHeight >= MIN_PANEL_HEIGHT) {
			panelHeight = Math.max(panelHeight, MIN_PANEL_HEIGHT);
		}

		int left = Math.max((this.width - panelWidth) / 2, Math.min(SCREEN_MARGIN, Math.max(0, this.width - panelWidth)));
		int top = Math.max((this.height - panelHeight) / 2, Math.min(SCREEN_MARGIN, Math.max(0, this.height - panelHeight)));
		boolean compact = panelWidth < PANEL_WIDTH || panelHeight < PANEL_HEIGHT;
		boolean veryCompact = panelHeight < 245;
		int innerMargin = panelWidth <= 260 ? 10 : compact ? 14 : 22;
		int innerLeft = left + innerMargin;
		int innerRight = left + panelWidth - innerMargin;
		int contentWidth = Math.max(100, innerRight - innerLeft);
		int buttonHeight = veryCompact ? 14 : compact ? 16 : 20;
		int labelStep = veryCompact ? 8 : 10;
		int rowGap = veryCompact ? 3 : compact ? 4 : 7;
		int groupGap = veryCompact ? 5 : compact ? 6 : 10;
		boolean showSubtitle = !veryCompact && panelWidth >= 280 && panelHeight >= 255;

		int titleY = top + (showSubtitle ? 14 : 7);
		int subtitleY = top + 28;
		int y = top + (showSubtitle ? 47 : 25);
		int topSeparatorY = y;
		y += veryCompact ? 3 : 5;
		int structureLabelY = y;
		int structureButtonY = structureLabelY + labelStep;
		y = structureButtonY + buttonHeight + rowGap;
		int speedLabelY = y;
		int speedButtonY = speedLabelY + labelStep;
		y = speedButtonY + buttonHeight + rowGap;
		int captureLabelY = y;
		int captureButtonY = captureLabelY + labelStep;
		y = captureButtonY + buttonHeight + groupGap;
		int actionsButtonY = y;
		y = actionsButtonY + buttonHeight + rowGap;
		int pauseButtonY = y;
		y = pauseButtonY + buttonHeight + rowGap;
		int cancelButtonY = y;
		y = cancelButtonY + buttonHeight + groupGap;
		int hintY = y;
		y = hintY + (veryCompact ? 12 : 16);
		int statusSeparatorY = Math.max(hintY + 10, y - 5);
		int statusTitleY = y;
		int statusModeY = statusTitleY + 10;
		int statusStructureY = statusModeY + 10;
		int statusProgressY = statusStructureY + 10;
		int statusEtaY = statusProgressY + 10;
		int statusTerrainY = statusEtaY + 10;
		int bottomLimit = top + panelHeight - 8;

		int folderButtonWidth = Math.min(compact ? 60 : 72, Math.max(46, contentWidth / 3));
		if (contentWidth - folderButtonWidth - 4 < 80) {
			folderButtonWidth = Math.max(42, contentWidth - 84);
		}
		int structureButtonWidth = Math.max(60, contentWidth - folderButtonWidth - 4);
		int splitLeft = Math.max(42, (contentWidth - 4) / 2);
		int splitRight = contentWidth - splitLeft - 4;
		if (splitRight < 42) {
			splitRight = 42;
			splitLeft = Math.max(42, contentWidth - splitRight - 4);
		}
		int halfWidth = Math.max(42, (contentWidth - 4) / 2);
		if (contentWidth - halfWidth - 4 < 42) {
			halfWidth = Math.max(42, contentWidth - 46);
		}
		int languageButtonWidth = Math.min(LANGUAGE_BUTTON_WIDTH, Math.max(48, contentWidth / 3));
		int youtubeButtonLeft = innerRight - YOUTUBE_BADGE_SIZE;
		int languageButtonLeft = Math.max(innerLeft, youtubeButtonLeft - 4 - languageButtonWidth);
		int headerButtonTop = top + (veryCompact ? 4 : 8);

		return new UiLayout(
			left,
			top,
			panelWidth,
			panelHeight,
			innerLeft,
			innerRight,
			contentWidth,
			buttonHeight,
			titleY,
			subtitleY,
			topSeparatorY,
			structureLabelY,
			structureButtonY,
			speedLabelY,
			speedButtonY,
			captureLabelY,
			captureButtonY,
			actionsButtonY,
			pauseButtonY,
			cancelButtonY,
			hintY,
			statusSeparatorY,
			statusTitleY,
			statusModeY,
			statusStructureY,
			statusProgressY,
			statusEtaY,
			statusTerrainY,
			structureButtonWidth,
			folderButtonWidth,
			splitLeft,
			splitRight,
			halfWidth,
			languageButtonLeft,
			headerButtonTop,
			languageButtonWidth,
			youtubeButtonLeft,
			headerButtonTop,
			showSubtitle,
			statusStructureY <= bottomLimit,
			statusEtaY <= bottomLimit,
			statusTerrainY <= bottomLimit
		);
	}

	private void reloadChoices() {
		reloadChoices(lastStructureKey);
	}

	private void reloadChoices(String preferredKey) {
		this.structureChoices = new ArrayList<>(StructureListClientState.entries());
		this.knownStructureListRevision = StructureListClientState.revision();
		if (this.structureChoices.isEmpty()) {
			this.structureChoices.add(StructureLibrary.defaultSelectionEntry());
		}

		this.selectedStructureIndex = 0;
		for (int i = 0; i < this.structureChoices.size(); i++) {
			if (this.structureChoices.get(i).key().equals(preferredKey)) {
				this.selectedStructureIndex = i;
				break;
			}
		}
	}

	private void sendControl(BuildControlAction action) {
		ClientPlayNetworking.send(new BuildControlPayload(action.networkId()));
	}

	private void startBuild() {
		StructureLibrary.SelectionEntry selected = currentSelection();
		lastStructureKey = selected.key();
		lastSpeed = selectedSpeed;

		ClientPlayNetworking.send(new BuildRequestPayload(selected.key(), selectedSpeed.networkId()));
		PreviewConfirmState.arm();
		this.onClose();
	}

	private void captureCustom() {
		lastCaptureFormat = selectedCaptureFormat;
		ClientPlayNetworking.send(new CaptureRequestPayload(selectedCaptureFormat.networkId()));
	}

	private StructureLibrary.SelectionEntry currentSelection() {
		return this.structureChoices.get(this.selectedStructureIndex);
	}

	private Component structureMessage() {
		return Component.translatable("screen.grand_builder.structure_value", currentSelection().displayName());
	}

	private Component speedMessage() {
		return Component.translatable(
			"screen.grand_builder.speed_value",
			Component.translatable(selectedSpeed.translationKey()),
			selectedSpeed.displayRate()
		);
	}

	private Component captureFormatMessage() {
		return Component.translatable(
			"screen.grand_builder.capture_format_value",
			Component.translatable(selectedCaptureFormat.translationKey())
		);
	}

	private Component terrainMessage() {
		return Component.translatable(
			this.terrainEnabled
				? "screen.grand_builder.terrain_enabled"
				: "screen.grand_builder.terrain_disabled"
		);
	}

	private Component languageMessage() {
		return Component.translatable("screen.grand_builder.language_value", languageDisplayCode(currentLanguageCode()));
	}

	private Component fitButtonMessage(Component message, int buttonWidth) {
		return fitText(message, Math.max(8, buttonWidth - 12));
	}

	private Component fitText(Component message, int maxWidth) {
		if (this.font == null || maxWidth <= 0) {
			return message;
		}

		String text = message.getString();
		if (this.font.width(text) <= maxWidth) {
			return message;
		}

		String ellipsis = "...";
		int ellipsisWidth = this.font.width(ellipsis);
		if (maxWidth <= ellipsisWidth) {
			return Component.literal(ellipsis);
		}
		return Component.literal(this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth).trim() + ellipsis);
	}

	private void setFittedMessage(Button button, Component message) {
		if (button != null) {
			button.setMessage(fitButtonMessage(message, button.getWidth()));
		}
	}

	private String currentLanguageCode() {
		if (this.minecraft == null) {
			return ENGLISH_LANGUAGE;
		}
		return this.minecraft.options.languageCode;
	}

	private String languageDisplayCode(String languageCode) {
		if (RUSSIAN_LANGUAGE.equals(languageCode)) {
			return "RU";
		}
		if (ENGLISH_LANGUAGE.equals(languageCode)) {
			return "EN";
		}
		return languageCode.toUpperCase(Locale.ROOT);
	}

	private void switchLanguage() {
		if (this.minecraft == null) {
			return;
		}

		String nextLanguage = RUSSIAN_LANGUAGE.equals(currentLanguageCode())
			? ENGLISH_LANGUAGE
			: RUSSIAN_LANGUAGE;
		this.minecraft.getLanguageManager().setSelected(nextLanguage);
		this.minecraft.options.languageCode = nextLanguage;
		this.minecraft.options.save();
		refreshButtonMessages();
		this.minecraft.reloadResourcePacks().thenRun(() -> {
			if (this.minecraft != null) {
				this.minecraft.execute(this::refreshButtonMessages);
			}
		});
		if (this.minecraft.player != null) {
			this.minecraft.player.displayClientMessage(
				Component.translatable("screen.grand_builder.language_changed", languageDisplayCode(nextLanguage)),
				true
			);
		}
	}

	private void refreshButtonMessages() {
		setFittedMessage(this.structureButton, structureMessage());
		setFittedMessage(this.folderButton, Component.translatable("screen.grand_builder.open_structures"));
		setFittedMessage(this.speedButton, speedMessage());
		setFittedMessage(this.terrainButton, terrainMessage());
		setFittedMessage(this.captureFormatButton, captureFormatMessage());
		setFittedMessage(this.startButton, Component.translatable("screen.grand_builder.start"));
		setFittedMessage(this.captureButton, Component.translatable("screen.grand_builder.capture"));
		setFittedMessage(this.pauseResumeButton, Component.translatable("screen.grand_builder.pause_resume"));
		setFittedMessage(this.rollbackButton, Component.translatable("screen.grand_builder.rollback"));
		setFittedMessage(this.cancelPreviewButton, Component.translatable("screen.grand_builder.cancel_preview"));
		setFittedMessage(this.languageButton, languageMessage());
		if (this.youtubeButton != null) {
			this.youtubeButton.setMessage(Component.translatable("screen.grand_builder.youtube.badge"));
		}
	}

	@Override
	public void tick() {
		super.tick();
		syncStructureChoicesFromServer();
		syncSpeedFromServer();
		YoutubeChannelFeed.requestRefreshIfNeeded();
		if (--statusPollCooldown <= 0) {
			sendControl(BuildControlAction.STATUS_SILENT);
			statusPollCooldown = 12;
		}
	}

	private void syncStructureChoicesFromServer() {
		if (StructureListClientState.revision() == this.knownStructureListRevision) {
			return;
		}

		String selectedKey = this.structureChoices.isEmpty()
			? lastStructureKey
			: currentSelection().key();
		reloadChoices(selectedKey);
		if (this.structureButton != null) {
			setFittedMessage(this.structureButton, structureMessage());
		}
	}

	private void syncSpeedFromServer() {
		BuildStatusClientState.Snapshot snapshot = BuildStatusClientState.snapshot();
		BuildSpeed serverSpeed = BuildSpeed.byNetworkId(snapshot.speedId());
		if (serverSpeed != this.selectedSpeed) {
			this.selectedSpeed = serverSpeed;
			if (this.speedButton != null) {
				setFittedMessage(this.speedButton, speedMessage());
			}
		}
		if (snapshot.terrainAdaptationEnabled() != this.terrainEnabled) {
			this.terrainEnabled = snapshot.terrainAdaptationEnabled();
			if (this.terrainButton != null) {
				setFittedMessage(this.terrainButton, terrainMessage());
			}
		}
	}

	private void drawFittedString(GuiGraphics guiGraphics, Component message, int x, int y, int maxWidth, int color) {
		guiGraphics.drawString(this.font, fitText(message, maxWidth), x, y, color);
	}

	private void drawCenteredFittedString(GuiGraphics guiGraphics, Component message, int centerX, int y, int maxWidth, int color) {
		guiGraphics.drawCenteredString(this.font, fitText(message, maxWidth), centerX, y, color);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(guiGraphics);

		guiGraphics.fillGradient(0, 0, this.width, this.height, 0xCC0E1A2D, 0xCC081018);

		UiLayout layout = layout();
		int left = layout.left();
		int top = layout.top();
		int right = left + layout.panelWidth();
		int bottom = top + layout.panelHeight();
		int panelCenterX = left + layout.panelWidth() / 2;

		guiGraphics.fill(left - 3, top - 3, right + 3, bottom + 3, 0xF02A4D73);
		guiGraphics.fillGradient(left, top, right, bottom, 0xF0142234, 0xF01B314A);
		guiGraphics.fill(left + 16, layout.topSeparatorY(), right - 16, layout.topSeparatorY() + 1, 0x66B5E8FF);
		guiGraphics.fill(left + 16, layout.captureLabelY() - 3, right - 16, layout.captureLabelY() - 2, 0x336A90B5);
		if (layout.statusSeparatorY() < bottom - 12) {
			guiGraphics.fill(left + 16, layout.statusSeparatorY(), right - 16, layout.statusSeparatorY() + 1, 0x33577EA3);
		}

		drawCenteredFittedString(guiGraphics, Component.translatable("screen.grand_builder.title"), panelCenterX, layout.titleY(), Math.max(80, layout.contentWidth() - 90), 0xFFF6FAFF);
		if (layout.showSubtitle()) {
			drawCenteredFittedString(guiGraphics, Component.translatable("screen.grand_builder.subtitle"), panelCenterX, layout.subtitleY(), layout.contentWidth(), 0xFFB3D2F0);
		}
		drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.structure"), layout.innerLeft() + 2, layout.structureLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.speed"), layout.innerLeft() + 2, layout.speedLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.capture_format"), layout.innerLeft() + 2, layout.captureLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		renderLiveStatus(guiGraphics, layout);
		BuildStatusClientState.Snapshot snapshot = BuildStatusClientState.snapshot();
		drawCenteredFittedString(
			guiGraphics,
			Component.translatable(snapshot.modeId() == 2 ? "screen.grand_builder.hint_preview" : "screen.grand_builder.hint"),
			panelCenterX,
			layout.hintY(),
			layout.contentWidth(),
			0xFF95B6D8
		);

		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderStructuresFolderTooltip(guiGraphics, mouseX, mouseY);
		renderLanguageTooltip(guiGraphics, mouseX, mouseY);
		renderYoutubeBadge(guiGraphics, mouseX, mouseY);
	}

	private void renderLanguageTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (languageButton == null || !languageButton.isHovered()) {
			return;
		}

		List<String> tooltipLines = new ArrayList<>();
		tooltipLines.add(Component.translatable("screen.grand_builder.language_tooltip").getString());
		renderTextTooltip(guiGraphics, mouseX + 12, mouseY - 6, tooltipLines, LANGUAGE_TOOLTIP_MAX_WIDTH);
	}

	private void renderStructuresFolderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (folderButton == null || !folderButton.isHovered()) {
			return;
		}

		Path folder = StructureLibrary.structuresDirectory().toAbsolutePath();
		List<String> tooltipLines = new ArrayList<>();
		tooltipLines.add(Component.translatable("screen.grand_builder.structures_folder.title").getString());
		tooltipLines.add(Component.translatable("screen.grand_builder.structures_folder.path", folder).getString());
		tooltipLines.add(Component.translatable("screen.grand_builder.structures_folder.formats").getString());
		tooltipLines.add(Component.translatable("screen.grand_builder.structures_folder.click_hint").getString());
		renderTextTooltip(guiGraphics, mouseX + 12, mouseY - 6, tooltipLines, STRUCTURES_TOOLTIP_MAX_WIDTH);
	}

	private void renderYoutubeBadge(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (youtubeButton == null || !youtubeButton.isHovered()) {
			return;
		}

		YoutubeChannelFeed.Snapshot snapshot = YoutubeChannelFeed.snapshot();
		List<String> tooltipLines = new ArrayList<>();
		tooltipLines.add(Component.translatable("screen.grand_builder.youtube.title").getString());
		switch (snapshot.state()) {
			case LOADING -> tooltipLines.add(Component.translatable("screen.grand_builder.youtube.loading").getString());
			case ERROR -> tooltipLines.add(Component.translatable("screen.grand_builder.youtube.unavailable").getString());
			case LIVE -> tooltipLines.add(
				Component.translatable("screen.grand_builder.youtube.live_now", snapshot.latestTitle()).getString()
			);
			case READY -> tooltipLines.add(
				Component.translatable("screen.grand_builder.youtube.latest", snapshot.latestTitle()).getString()
			);
		}
		tooltipLines.add(Component.translatable("screen.grand_builder.youtube.click_hint").getString());

		renderTextTooltip(guiGraphics, mouseX + 12, mouseY - 6, tooltipLines, YOUTUBE_TOOLTIP_MAX_WIDTH);
	}

	private void renderTextTooltip(GuiGraphics guiGraphics, int startX, int startY, List<String> rawLines, int maxWidth) {
		List<String> lines = new ArrayList<>();
		int usableMaxWidth = Math.max(40, Math.min(maxWidth, this.width - 16));
		for (String line : rawLines) {
			lines.addAll(wrapLine(line, usableMaxWidth));
		}
		if (lines.isEmpty()) {
			return;
		}

		int width = 0;
		for (String line : lines) {
			width = Math.max(width, this.font.width(line));
		}
		int height = lines.size() * 10 + 6;

		int x = Math.max(6, Math.min(startX, this.width - width - 10));
		int y = Math.max(6, Math.min(startY, this.height - height - 6));

		guiGraphics.fill(x - 3, y - 3, x + width + 5, y + height + 3, 0xE0000000);
		guiGraphics.fill(x - 2, y - 2, x + width + 4, y + height + 2, 0xEE1D2736);

		int lineY = y + 1;
		for (String line : lines) {
			guiGraphics.drawString(this.font, line, x, lineY, 0xFFF2F7FF);
			lineY += 10;
		}
	}

	private List<String> wrapLine(String text, int maxWidth) {
		List<String> wrapped = new ArrayList<>();
		if (text == null || text.isBlank()) {
			wrapped.add("");
			return wrapped;
		}

		String remaining = text.trim();
		while (!remaining.isEmpty()) {
			if (this.font.width(remaining) <= maxWidth) {
				wrapped.add(remaining);
				break;
			}

			int cut = remaining.length();
			while (cut > 1 && this.font.width(remaining.substring(0, cut)) > maxWidth) {
				cut--;
			}

			int space = remaining.lastIndexOf(' ', cut - 1);
			if (space > 0) {
				cut = space;
			}

			String part = remaining.substring(0, cut).trim();
			if (part.isEmpty()) {
				part = remaining.substring(0, Math.min(1, remaining.length()));
			}
			wrapped.add(part);
			remaining = remaining.substring(Math.min(remaining.length(), cut)).trim();
		}

		return wrapped;
	}

	private void openYoutubeChannel() {
		try {
			Util.getPlatform().openUri(YoutubeChannelFeed.CHANNEL_URL);
			if (this.minecraft != null) {
				this.minecraft.keyboardHandler.setClipboard(YoutubeChannelFeed.CHANNEL_URL);
			}
			if (this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(Component.translatable("screen.grand_builder.youtube.opened"), true);
			}
		} catch (Exception ignored) {
			if (this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(Component.translatable("screen.grand_builder.youtube.unavailable"), true);
			}
		}
	}

	private void openStructuresFolder() {
		Path folder = StructureLibrary.structuresDirectory().toAbsolutePath();
		try {
			Util.getPlatform().openUri(folder.toUri().toString());
			if (this.minecraft != null) {
				this.minecraft.keyboardHandler.setClipboard(folder.toString());
			}
			if (this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(Component.translatable("screen.grand_builder.structures_folder.opened"), true);
			}
		} catch (Exception ignored) {
			if (this.minecraft != null) {
				this.minecraft.keyboardHandler.setClipboard(folder.toString());
			}
			if (this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(Component.translatable("screen.grand_builder.structures_folder.copied"), true);
			}
		}
	}

	private void renderLiveStatus(GuiGraphics guiGraphics, UiLayout layout) {
		BuildStatusClientState.Snapshot snapshot = BuildStatusClientState.snapshot();
		BuildSpeed speed = BuildSpeed.byNetworkId(snapshot.speedId());
		String speedRateText = snapshot.speedBlocksPerTick() > 0.0f
			? String.format(Locale.US, "%.2f", snapshot.speedBlocksPerTick())
			: speed.displayRate();

		Component modeText = switch (snapshot.modeId()) {
			case 1 -> snapshot.paused()
				? Component.translatable("screen.grand_builder.live_mode_paused")
				: Component.translatable("screen.grand_builder.live_mode_building");
			case 2 -> Component.translatable("screen.grand_builder.live_mode_preview");
			default -> Component.translatable("screen.grand_builder.live_mode_none");
		};

		Component structureLine = Component.translatable(
			"screen.grand_builder.live_structure",
			snapshot.structureName().isBlank() ? "-" : snapshot.structureName()
		);
		String progressText = String.format(Locale.US, "%.1f", snapshot.progressPercent());
		Component progressLine = Component.translatable(
			"screen.grand_builder.live_progress",
			progressText,
			snapshot.remainingBlocks()
		);
		Component etaLine = Component.translatable(
			"screen.grand_builder.live_eta",
			formatEtaTicks(snapshot.etaTicks()),
			Component.translatable(speed.translationKey()),
			speedRateText
		);

		int x = layout.innerLeft() + 2;
		int maxWidth = layout.contentWidth();
		int bottomLimit = layout.top() + layout.panelHeight() - 8;
		if (layout.statusTitleY() > bottomLimit) {
			return;
		}

		drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.live_title"), x, layout.statusTitleY(), maxWidth, 0xFFF2F7FF);
		if (layout.statusModeY() <= bottomLimit) {
			drawFittedString(guiGraphics, modeText, x, layout.statusModeY(), maxWidth, 0xFFB9D8F6);
		}
		if (layout.showStatusStructure()) {
			drawFittedString(guiGraphics, structureLine, x, layout.statusStructureY(), maxWidth, 0xFF9EC2E6);
		}
		if (layout.statusProgressY() <= bottomLimit) {
			drawFittedString(guiGraphics, progressLine, x, layout.statusProgressY(), maxWidth, 0xFF9EC2E6);
		}
		if (layout.showStatusEta()) {
			drawFittedString(guiGraphics, etaLine, x, layout.statusEtaY(), maxWidth, 0xFF9EC2E6);
		}
		if (layout.showStatusTerrain()) {
			drawFittedString(
				guiGraphics,
				Component.translatable(
					snapshot.terrainAdaptationEnabled()
						? "screen.grand_builder.live_terrain_on"
						: "screen.grand_builder.live_terrain_off"
				),
				x,
				layout.statusTerrainY(),
				maxWidth,
				0xFF9EC2E6
			);
		}
	}

	private static String formatEtaTicks(int ticks) {
		if (ticks <= 0) {
			return "--:--";
		}

		long totalSeconds = Math.max(1L, (ticks + 19L) / 20L);
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		return String.format(Locale.US, "%02d:%02d", minutes, seconds);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
