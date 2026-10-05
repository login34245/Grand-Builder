package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.BuildOptions;
import dev.grandbuilder.build.PlacementPolicy;
import dev.grandbuilder.build.BuildStartSide;
import dev.grandbuilder.build.DismantleStyle;
import dev.grandbuilder.build.CustomCaptureFormat;
import dev.grandbuilder.build.StructureLibrary;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.network.BuildRequestPayload;
import dev.grandbuilder.network.BuildEstimateRequestPayload;
import dev.grandbuilder.network.BuildEstimatePayload;
import dev.grandbuilder.network.BuildSetSpeedPayload;
import dev.grandbuilder.network.CaptureRequestPayload;
import dev.grandbuilder.network.StructureInspectRequestPayload;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class BuilderMenuScreen extends Screen {
	private static final int PANEL_WIDTH = 346;
	private static final int PANEL_HEIGHT = 356;
	private static final int MIN_PANEL_WIDTH = 220;
	private static final int MIN_PANEL_HEIGHT = 190;
	private static final int SCREEN_MARGIN = 6;
	private static final int YOUTUBE_BADGE_SIZE = 18;
	private static final int YOUTUBE_TOOLTIP_MAX_WIDTH = 220;
	private static final int STRUCTURES_TOOLTIP_MAX_WIDTH = 260;

	private static String lastStructureKey = StructureLibrary.defaultSelectionEntry().key();
	private static BuildOptions lastOptions = BuildOptions.DEFAULT;
	private static int nextEstimateRequestId;
	private static CustomCaptureFormat lastCaptureFormat = CustomCaptureFormat.SCHEM_SINGLE;

	private List<StructureLibrary.SelectionEntry> structureChoices = new ArrayList<>();
	private int selectedStructureIndex;
	private BuildSpeed selectedSpeed = BuilderTipPreferences.get().defaultSpeed();
	private BuildEffectMode selectedEffectMode = BuilderTipPreferences.get().defaultEffect();
	private BuildOptions selectedOptions = new BuildOptions(lastOptions.startSide(), lastOptions.dismantleStyle(), false, BuilderTipPreferences.get().defaultPlacement());
	private BuildEstimatePayload estimate;
	private BuildRequestPayload estimateSelection;
	private int estimateRequestId;
	private int estimateDelay;
	private int estimateWaitTicks;
	private CustomCaptureFormat selectedCaptureFormat = lastCaptureFormat;
	private Button structureButton;
	private Button folderButton;
	private Button inspectButton;
	private Button importButton;
	private Button speedButton;
	private Button terrainButton;
	private CycleButton<PlacementPolicy> replacementButton;
	private Button effectButton;
	private Button optionsButton;
	private Button captureFormatButton;
	private Button startButton;
	private Button captureButton;
	private Button pauseResumeButton;
	private Button rollbackButton;
	private Button cancelPreviewButton;
	private Button youtubeButton;
	private FilmingTabButton filmingTab;
	private FilmingTabButton settingsTab;
	private boolean initializedDefaults;
	private boolean checkedAutomaticTip;
	private boolean terrainEnabled = BuilderTipPreferences.get().defaultTerrain();
	private int statusPollCooldown = 0;
	private int knownStructureListRevision = -1;
	private int knownStatusRevision = BuildStatusClientState.revision();
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
		int replacementButtonY,
		int effectLabelY,
		int effectButtonY,
		int optionsButtonY,
		int etaY,
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
		int inspectButtonWidth,
		int importButtonWidth,
		int splitLeft,
		int splitRight,
		int halfWidth,
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
	public static void rememberStructure(String key) { lastStructureKey=key; }

	@Override
	protected void init() {
		PreviewConfirmState.disarm();
		if (!initializedDefaults) {
			initializedDefaults = true;
			ClientPlayNetworking.send(new dev.grandbuilder.network.BuildDefaultsPayload(selectedSpeed.networkId(), terrainEnabled));
		}
		sendControl(BuildControlAction.REQUEST_STRUCTURE_LIST);
		reloadChoices();

		UiLayout layout = layout();
		int actionRightWidth = layout.contentWidth() - layout.halfWidth() - 4;

		this.structureButton = this.addRenderableWidget(Button.builder(fitButtonMessage(structureMessage(), layout.structureButtonWidth()), button -> {
			if (BuilderTipPreferences.get().structureList()) minecraft.setScreen(new StructureSelectionScreen(this, currentSelection().key()));
			else selectStructure(this.structureChoices.get((selectedStructureIndex + 1) % structureChoices.size()).key());
		}).bounds(layout.innerLeft(), layout.structureButtonY(), layout.structureButtonWidth(), layout.buttonHeight()).build());
		this.folderButton = this.addRenderableWidget(Button.builder(
			fitButtonMessage(Component.translatable("screen.grand_builder.open_structures"), layout.folderButtonWidth()),
			button -> openStructuresFolder()
		).bounds(layout.innerLeft() + layout.structureButtonWidth() + 4, layout.structureButtonY(), layout.folderButtonWidth(), layout.buttonHeight()).build());
		this.inspectButton = this.addRenderableWidget(Button.builder(Component.literal("3D"), button -> {
			lastStructureKey = currentSelection().key();
			ClientPlayNetworking.send(new StructureInspectRequestPayload(currentSelection().key()));
			this.minecraft.setScreen(new StructureInspectScreen(dev.grandbuilder.network.StructurePreviewPayload.INSPECT, this));
		}).bounds(layout.innerLeft()+layout.structureButtonWidth()+layout.folderButtonWidth()+8,
			layout.structureButtonY(),layout.inspectButtonWidth(),layout.buttonHeight()).build());
		this.inspectButton.setTooltip(Tooltip.create(Component.translatable("screen.grand_builder.inspect_tooltip")));
		this.importButton = this.addRenderableWidget(Button.builder(
			fitButtonMessage(Component.translatable("screen.grand_builder.import.button"),layout.importButtonWidth()),
			button -> this.minecraft.setScreen(new WorldImportScreen()))
			.bounds(layout.innerLeft()+layout.structureButtonWidth()+layout.folderButtonWidth()+layout.inspectButtonWidth()+12,
				layout.structureButtonY(),layout.importButtonWidth(),layout.buttonHeight()).build());

		this.speedButton = this.addRenderableWidget(Button.builder(fitButtonMessage(speedMessage(), layout.splitLeft()), button -> {
			this.selectedSpeed = this.selectedSpeed.next();
			setFittedMessage(this.speedButton, speedMessage());
			ClientPlayNetworking.send(new BuildSetSpeedPayload(this.selectedSpeed.networkId()));
		}).bounds(layout.innerLeft(), layout.speedButtonY(), layout.splitLeft(), layout.buttonHeight()).build());
		this.terrainButton = this.addRenderableWidget(Button.builder(fitButtonMessage(terrainMessage(), layout.splitRight()), button -> {
			this.terrainEnabled = !this.terrainEnabled;
			setFittedMessage(this.terrainButton, terrainMessage());
			if (BuildStatusClientState.snapshot().modeId() == 0)
				ClientPlayNetworking.send(new dev.grandbuilder.network.BuildDefaultsPayload(selectedSpeed.networkId(), terrainEnabled));
			else sendControl(BuildControlAction.TOGGLE_TERRAIN);
		}).bounds(layout.innerLeft() + layout.splitLeft() + 4, layout.speedButtonY(), layout.splitRight(), layout.buttonHeight()).build());
		this.replacementButton = this.addRenderableWidget(CycleButton.<PlacementPolicy>builder(value ->
			fitButtonMessage(Component.translatable(value.translationKey()), layout.contentWidth()), selectedOptions.placementPolicy())
			.withValues(PlacementPolicy.values()).displayOnlyValue()
			.withTooltip(value -> Tooltip.create(Component.translatable(value.tooltipKey())))
			.create(layout.innerLeft(), layout.replacementButtonY(), layout.contentWidth(), layout.buttonHeight(),
				Component.translatable("screen.grand_builder.existing_blocks"), (button, replace) -> {
					selectedOptions = new BuildOptions(selectedOptions.startSide(), selectedOptions.dismantleStyle(),
						selectedOptions.destructiveExplosion(), replace).normalized(selectedEffectMode);
					lastOptions = selectedOptions;
					refreshButtonMessages();
				}));

		this.effectButton = this.addRenderableWidget(Button.builder(fitButtonMessage(effectMessage(), layout.contentWidth()), button -> {
			this.selectedEffectMode = this.selectedEffectMode.next();
			this.selectedOptions = new BuildOptions(selectedOptions.startSide(), selectedOptions.dismantleStyle(), false, selectedOptions.placementPolicy());
			setFittedMessage(this.effectButton, effectMessage());
			updateEffectDependentControls();
		}).bounds(layout.innerLeft(), layout.effectButtonY(), layout.contentWidth(), layout.buttonHeight()).build());
		this.optionsButton = this.addRenderableWidget(Button.builder(optionsMessage(), button -> {
			if (selectedEffectMode == BuildEffectMode.REVERSE) selectedOptions = new BuildOptions(selectedOptions.startSide().next(), selectedOptions.dismantleStyle(), false, selectedOptions.placementPolicy());
			else if (selectedEffectMode == BuildEffectMode.DISMANTLE) selectedOptions = new BuildOptions(selectedOptions.startSide(), selectedOptions.dismantleStyle().next(), false, selectedOptions.placementPolicy());
			else if (selectedEffectMode == BuildEffectMode.BUILDER_CHARGE) selectedOptions = new BuildOptions(selectedOptions.startSide(), selectedOptions.dismantleStyle(), !selectedOptions.destructiveExplosion(), selectedOptions.placementPolicy()).normalized(selectedEffectMode);
			lastOptions = selectedOptions;
			refreshButtonMessages();
		}).bounds(layout.innerLeft(), layout.optionsButtonY(), layout.contentWidth(), layout.buttonHeight()).build());

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
		this.filmingTab = this.addRenderableWidget(new FilmingTabButton(() -> {
			UiLayout current = layout();
			return FilmingTabLayout.at(width, current.left(), current.top(), current.panelWidth(),
				filmingTab == null ? 0 : filmingTab.expansion());
		}, button -> minecraft.setScreen(new FilmingToolsScreen(this))));
		this.settingsTab = this.addRenderableWidget(new FilmingTabButton(() -> {
			UiLayout current = layout();
			return FilmingTabLayout.at(width, current.left(), current.top(), current.panelWidth(),
				settingsTab == null ? 0 : settingsTab.expansion(), 1);
		}, button -> minecraft.setScreen(new BuilderSettingsScreen(this)),
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPARATOR),
			"screen.grand_builder.settings.title", "screen.grand_builder.settings.tab"));
		updateEffectDependentControls();

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
		boolean compact = panelWidth < PANEL_WIDTH || panelHeight < 310;
		boolean veryCompact = panelHeight < 245;
		int innerMargin = panelWidth <= 260 ? 10 : compact ? 14 : 22;
		int innerLeft = left + innerMargin;
		int innerRight = left + panelWidth - innerMargin;
		int contentWidth = Math.max(100, innerRight - innerLeft);
		int labelStep = panelHeight >= 250 ? 10 : panelHeight >= 210 ? 8 : 0;
		int rowGap = panelHeight >= 310 ? 4 : 2;
		int groupGap = rowGap;
		boolean showSubtitle = false;
		boolean showSpeed = !selectedEffectMode.hidesSpeed();
		int rows = hasModeOptions() ? 9 : 8;
		int footerHeight = panelHeight >= 310 ? 54 : 34;
		int available = panelHeight - 35 - footerHeight - labelStep*(showSpeed ? 4 : 3) - rowGap*(rows+1);
		int buttonHeight = Math.max(10, Math.min(20, available / rows));
		int titleY = top + 5;
		int subtitleY = top + 18;
		int etaY = top + 18;
		int topSeparatorY = top + 30;
		int y = top + 35;
		int structureLabelY = labelStep > 0 ? y : -100;
		int structureButtonY = y + labelStep;
		y = structureButtonY + buttonHeight + rowGap;
		int speedLabelY = showSpeed && labelStep > 0 ? y : -100;
		int speedButtonY = y + (showSpeed ? labelStep : 0);
		y = speedButtonY + buttonHeight + rowGap;
		int replacementButtonY = y;
		y += buttonHeight + rowGap;
		int effectLabelY = labelStep > 0 ? y : -100;
		int effectButtonY = y + labelStep;
		y = effectButtonY + buttonHeight + rowGap;
		int optionsButtonY = y;
		if (hasModeOptions()) y += buttonHeight + rowGap;
		int captureLabelY = labelStep > 0 ? y : -100;
		int captureButtonY = y + labelStep;
		y = captureButtonY + buttonHeight + groupGap;
		int actionsButtonY = y;
		y = actionsButtonY + buttonHeight + rowGap;
		int pauseButtonY = y;
		y = pauseButtonY + buttonHeight + rowGap;
		int cancelButtonY = y;
		y = cancelButtonY + buttonHeight + groupGap;
		int hintY = -100;
		int statusSeparatorY = y;
		int statusTitleY = y + 4;
		int statusModeY = statusTitleY + 10;
		int statusStructureY = statusModeY + 10;
		int statusProgressY = statusModeY + (panelHeight >= 310 ? 20 : 10);
		int statusEtaY = statusProgressY + 10;
		int statusTerrainY = statusEtaY + 10;
		int bottomLimit = top + panelHeight - 8;

		int folderButtonWidth = compact ? 46 : 56;
		int inspectButtonWidth = 26;
		int importButtonWidth = compact ? 42 : 50;
		int structureButtonWidth = Math.max(56, contentWidth - folderButtonWidth - inspectButtonWidth - importButtonWidth - 12);
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
		int youtubeButtonLeft = innerRight - YOUTUBE_BADGE_SIZE;
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
			replacementButtonY,
			effectLabelY,
			effectButtonY,
			optionsButtonY,
			etaY,
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
			inspectButtonWidth,
			importButtonWidth,
			splitLeft,
			splitRight,
			halfWidth,
			youtubeButtonLeft,
			headerButtonTop,
			showSubtitle,
			panelHeight >= 310 && statusStructureY <= bottomLimit,
			false,
			false
		);
	}

	private void reloadChoices() {
		reloadChoices(lastStructureKey);
	}

	List<StructureLibrary.SelectionEntry> selectionEntries() { return List.copyOf(structureChoices); }
	void refreshSelections() { syncStructureChoicesFromServer(); }
	void selectStructure(String key) {
		for (int i = 0; i < structureChoices.size(); i++) if (structureChoices.get(i).key().equals(key)) {
			selectedStructureIndex = i;
			lastStructureKey = key;
			setFittedMessage(structureButton, structureMessage());
			return;
		}
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
		if (selectedEffectMode == BuildEffectMode.BUILDER_CHARGE && selectedOptions.destructiveExplosion() && this.minecraft != null) {
			this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
				if (confirmed) sendBuildRequest();
				else this.minecraft.setScreen(this);
			}, Component.translatable("screen.grand_builder.destructive_title"), Component.translatable("screen.grand_builder.destructive_warning")));
			return;
		}
		sendBuildRequest();
	}

	private BuildRequestPayload selectionPayload() {
		BuildOptions options = selectedOptions.normalized(selectedEffectMode);
		return new BuildRequestPayload(currentSelection().key(), selectedSpeed.networkId(), selectedEffectMode.networkId(),
			options.startSide().ordinal(), options.dismantleStyle().ordinal(), options.destructiveExplosion(), options.placementPolicy().ordinal());
	}

	private void sendBuildRequest() {
		StructureLibrary.SelectionEntry selected = currentSelection();
		lastStructureKey = selected.key();

		lastOptions = selectedOptions;
		ClientPlayNetworking.send(selectionPayload());
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
			selectedOptions.displayRate(selectedEffectMode, selectedSpeed)
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
			this.terrainEnabled && !selectedOptions.clearsSite()
				? "screen.grand_builder.terrain_enabled"
				: "screen.grand_builder.terrain_disabled"
		);
	}

	private Component effectMessage() {
		return Component.translatable(
			"screen.grand_builder.effect_value",
			Component.translatable(selectedEffectMode.translationKey())
		);
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

	private void refreshButtonMessages() {
		setFittedMessage(this.structureButton, structureMessage());
		setFittedMessage(this.folderButton, Component.translatable("screen.grand_builder.open_structures"));
		setFittedMessage(this.importButton, Component.translatable("screen.grand_builder.import.button"));
		setFittedMessage(this.speedButton, speedMessage());
		setFittedMessage(this.terrainButton, terrainMessage());
		setFittedMessage(this.effectButton, effectMessage());
		setFittedMessage(this.optionsButton, optionsMessage());
		setFittedMessage(this.captureFormatButton, captureFormatMessage());
		setFittedMessage(this.startButton, Component.translatable("screen.grand_builder.start"));
		setFittedMessage(this.captureButton, Component.translatable("screen.grand_builder.capture"));
		setFittedMessage(this.pauseResumeButton, Component.translatable("screen.grand_builder.pause_resume"));
		setFittedMessage(this.rollbackButton, Component.translatable("screen.grand_builder.rollback"));
		setFittedMessage(this.cancelPreviewButton, Component.translatable("screen.grand_builder.cancel_preview"));
		if (this.youtubeButton != null) {
			this.youtubeButton.setMessage(Component.translatable("screen.grand_builder.youtube.badge"));
		}
		updateEffectDependentControls();
	}

	private void updateEffectDependentControls() {
		UiLayout layout = layout();
		boolean showSpeed = !selectedEffectMode.hidesSpeed();
		boolean showTerrain = selectedEffectMode != BuildEffectMode.DISMANTLE;
		if (this.structureButton != null) this.structureButton.setY(layout.structureButtonY());
		if (this.folderButton != null) this.folderButton.setY(layout.structureButtonY());
		if (this.inspectButton != null) this.inspectButton.setY(layout.structureButtonY());
		if (this.importButton != null) this.importButton.setY(layout.structureButtonY());
		if (this.replacementButton != null) {
			this.replacementButton.setY(layout.replacementButtonY());
			this.replacementButton.setHeight(layout.buttonHeight());
			this.replacementButton.setValue(selectedOptions.placementPolicy());
		}
		if (this.effectButton != null) this.effectButton.setY(layout.effectButtonY());
		if (this.captureFormatButton != null) this.captureFormatButton.setY(layout.captureButtonY());
		if (this.startButton != null) this.startButton.setY(layout.actionsButtonY());
		if (this.captureButton != null) this.captureButton.setY(layout.actionsButtonY());
		if (this.pauseResumeButton != null) this.pauseResumeButton.setY(layout.pauseButtonY());
		if (this.rollbackButton != null) this.rollbackButton.setY(layout.pauseButtonY());
		if (this.cancelPreviewButton != null) this.cancelPreviewButton.setY(layout.cancelButtonY());
		for (Button button : new Button[] {structureButton,folderButton,inspectButton,importButton,effectButton,captureFormatButton,startButton,captureButton,
			pauseResumeButton,rollbackButton,cancelPreviewButton}) if (button != null) button.setHeight(layout.buttonHeight());
		if (this.optionsButton != null) {
			this.optionsButton.visible = hasModeOptions();
			this.optionsButton.active = hasModeOptions() && (selectedEffectMode != BuildEffectMode.BUILDER_CHARGE || selectedOptions.replaceExistingBlocks());
			this.optionsButton.setY(layout.optionsButtonY());
			this.optionsButton.setHeight(layout.buttonHeight());
			setFittedMessage(this.optionsButton, optionsMessage());
			this.optionsButton.setTooltip(selectedEffectMode == BuildEffectMode.BUILDER_CHARGE
				? Tooltip.create(Component.translatable(!selectedOptions.replaceExistingBlocks()
					? "screen.grand_builder.blocks_keep_explosion" : selectedOptions.destructiveExplosion()
					? "screen.grand_builder.destructive_warning" : "screen.grand_builder.explosion_visual")) : null);
		}
		if (this.speedButton != null) {
			this.speedButton.visible = showSpeed;
			this.speedButton.active = showSpeed;
			this.speedButton.setX(layout.innerLeft());
			this.speedButton.setY(layout.speedButtonY());
			this.speedButton.setWidth(showTerrain ? layout.splitLeft() : layout.contentWidth());
			this.speedButton.setHeight(layout.buttonHeight());
			setFittedMessage(this.speedButton, speedMessage());
		}
		if (this.terrainButton != null) {
			this.terrainButton.visible = showTerrain;
			this.terrainButton.active = showTerrain && !selectedOptions.clearsSite();
			this.terrainButton.setTooltip(selectedOptions.clearsSite()
				? Tooltip.create(Component.translatable("screen.grand_builder.blocks_clear_tooltip")) : null);
			int terrainX = showSpeed ? layout.innerLeft() + layout.splitLeft() + 4 : layout.innerLeft();
			int terrainWidth = showSpeed ? layout.splitRight() : layout.contentWidth();
			this.terrainButton.setX(terrainX);
			this.terrainButton.setY(layout.speedButtonY());
			this.terrainButton.setWidth(terrainWidth);
			this.terrainButton.setHeight(layout.buttonHeight());
			setFittedMessage(this.terrainButton, terrainMessage());
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!checkedAutomaticTip) {
			checkedAutomaticTip = true;
			int tip = BuilderTipPreferences.get().takeAutomaticTip(System.currentTimeMillis());
			if (tip >= 0) { minecraft.setScreen(new BuilderTipScreen(this, tip)); return; }
		}
		syncStructureChoicesFromServer();
		syncSpeedFromServer();
		YoutubeChannelFeed.requestRefreshIfNeeded();
		updateEstimateRequest();
		if (--statusPollCooldown <= 0) {
			sendControl(BuildControlAction.STATUS_SILENT);
			statusPollCooldown = 12;
		}
	}

	private boolean hasModeOptions() {
		return selectedEffectMode == BuildEffectMode.DISMANTLE || selectedEffectMode == BuildEffectMode.REVERSE
			|| selectedEffectMode == BuildEffectMode.BUILDER_CHARGE;
	}

	private Component optionsMessage() {
		return switch (selectedEffectMode) {
			case REVERSE -> Component.translatable("screen.grand_builder.order_value",Component.translatable(selectedOptions.startSide().translationKey()));
			case DISMANTLE -> Component.translatable("screen.grand_builder.dismantle_value",Component.translatable(selectedOptions.dismantleStyle().translationKey()));
			case BUILDER_CHARGE -> Component.translatable(selectedOptions.destructiveExplosion()
				? "screen.grand_builder.explosion_destructive" : "screen.grand_builder.explosion_visual");
			default -> Component.empty();
		};
	}

	private void updateEstimateRequest() {
		BuildRequestPayload selection = selectionPayload();
		if (!selection.equals(estimateSelection)) {
			estimateSelection = selection;
			estimate = null;
			estimateDelay = 4;
			estimateWaitTicks = 0;
		}
		if (estimate != null) return;
		if (estimateDelay > 0 && --estimateDelay == 0 || estimateDelay == 0 && ++estimateWaitTicks >= 40) {
			estimateRequestId = ++nextEstimateRequestId;
			estimateWaitTicks = 0;
			ClientPlayNetworking.send(new BuildEstimateRequestPayload(estimateRequestId, selection));
		}
	}

	public void receiveEstimate(BuildEstimatePayload payload) {
		if (payload.requestId() == estimateRequestId && selectionPayload().equals(estimateSelection)) estimate = payload;
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
		if (knownStatusRevision == BuildStatusClientState.revision()) return;
		knownStatusRevision = BuildStatusClientState.revision();
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
		BuilderTheme theme = BuilderTheme.current();
		if (theme != BuilderTheme.CURRENT) color = color == 0xFFFFDEA3 ? theme.accent
			: color == 0xFFF2F7FF || color == 0xFFDBE9FF ? theme.text : theme.muted;
		guiGraphics.drawString(this.font, fitText(message, maxWidth), x, y, color);
	}

	private void drawCenteredFittedString(GuiGraphics guiGraphics, Component message, int centerX, int y, int maxWidth, int color) {
		if (BuilderTheme.current() != BuilderTheme.CURRENT && color == 0xFFB3D2F0) color = BuilderTheme.current().muted;
		guiGraphics.drawCenteredString(this.font, fitText(message, maxWidth), centerX, y, color);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(guiGraphics);

		BuilderTheme theme = BuilderTheme.current();
		guiGraphics.fillGradient(0, 0, this.width, this.height, theme.backdropTop, theme.backdropBottom);

		UiLayout layout = layout();
		int left = layout.left();
		int top = layout.top();
		int right = left + layout.panelWidth();
		int bottom = top + layout.panelHeight();
		int panelCenterX = left + layout.panelWidth() / 2;

		guiGraphics.fill(left - 3, top - 3, right + 3, bottom + 3, theme.border);
		guiGraphics.fillGradient(left, top, right, bottom, theme.panelTop, theme.panelBottom);
		guiGraphics.fill(left + 16, layout.topSeparatorY(), right - 16, layout.topSeparatorY() + 1, theme.muted & 0x66FFFFFF);
		if (layout.captureLabelY() >= 0) guiGraphics.fill(left + 16, layout.captureLabelY() - 3, right - 16, layout.captureLabelY() - 2, 0x336A90B5);
		if (layout.statusSeparatorY() < bottom - 12) {
			guiGraphics.fill(left + 16, layout.statusSeparatorY(), right - 16, layout.statusSeparatorY() + 1, 0x33577EA3);
		}

		boolean tabInHeader = FilmingTabLayout.at(width, left, top, layout.panelWidth(), 1).inHeader();
		int titleLeft = tabInHeader ? left + 2 * FilmingTabLayout.OPEN_WIDTH + 12 : layout.innerLeft();
		int titleRight = layout.youtubeButtonLeft() - 6;
		drawCenteredFittedString(guiGraphics, Component.translatable("screen.grand_builder.title"), (titleLeft + titleRight) / 2,
			layout.titleY(), Math.max(20, titleRight - titleLeft), theme.text);
		renderEta(guiGraphics, layout);
		if (layout.showSubtitle()) {
			drawCenteredFittedString(guiGraphics, Component.translatable("screen.grand_builder.subtitle"), panelCenterX, layout.subtitleY(), layout.contentWidth(), 0xFFB3D2F0);
		}
		if (layout.structureLabelY() >= 0) drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.structure"), layout.innerLeft() + 2, layout.structureLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		if (layout.speedLabelY() >= 0) {
			drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.speed"), layout.innerLeft() + 2, layout.speedLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		}
		if (layout.effectLabelY() >= 0) drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.effects"), layout.innerLeft() + 2, layout.effectLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		if (layout.captureLabelY() >= 0) drawFittedString(guiGraphics, Component.translatable("screen.grand_builder.capture_format"), layout.innerLeft() + 2, layout.captureLabelY(), layout.contentWidth(), 0xFFDBE9FF);
		renderLiveStatus(guiGraphics, layout);

		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderStructuresFolderTooltip(guiGraphics, mouseX, mouseY);
		renderYoutubeBadge(guiGraphics, mouseX, mouseY);
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

	private void renderEta(GuiGraphics graphics, UiLayout layout) {
		BuildStatusClientState.Snapshot status = BuildStatusClientState.snapshot();
		Component line;
		int color = 0xFFFFDEA3;
		if (status.modeId() != 0) {
			line = Component.translatable(status.paused() ? "screen.grand_builder.eta_paused" : "screen.grand_builder.eta_remaining",
				formatEtaTicks(status.etaTicks()));
		} else if (estimate == null) {
			line = Component.translatable("screen.grand_builder.eta_loading");
			color = 0xFFB9D8F6;
		} else if (!estimate.available()) {
			line = Component.translatable("screen.grand_builder.eta_unavailable");
			color = 0xFFB9D8F6;
		} else line = Component.translatable("screen.grand_builder.eta_estimate", formatEtaTicks(estimate.etaTicks()));
		drawFittedString(graphics,line,layout.innerLeft()+2,layout.etaY(),layout.contentWidth()-22,color);
	}

	private void renderLiveStatus(GuiGraphics guiGraphics, UiLayout layout) {
		BuildStatusClientState.Snapshot snapshot = BuildStatusClientState.snapshot();
		BuildSpeed speed = BuildSpeed.byNetworkId(snapshot.speedId());
		String speedRateText = snapshot.speedBlocksPerTick() > 0.0f
			? String.format(Locale.US, "%.2f", snapshot.speedBlocksPerTick())
			: selectedEffectMode.displayRate(speed);

		Component modeText = switch (snapshot.modeId()) {
			case 4 -> Component.translatable(snapshot.paused() ? "screen.grand_builder.live_mode_paused" : "screen.grand_builder.live_mode_clearing");
			case 1, 3 -> snapshot.paused()
				? Component.translatable("screen.grand_builder.live_mode_paused")
				: Component.translatable(snapshot.modeId() == 3 ? "screen.grand_builder.live_mode_dismantling" : "screen.grand_builder.live_mode_building");
			case 2 -> Component.translatable("screen.grand_builder.live_mode_preview");
			default -> Component.translatable("screen.grand_builder.live_mode_none");
		};

		Component structureLine = Component.translatable(
			"screen.grand_builder.live_structure",
			snapshot.structureName().isBlank() ? currentSelection().displayName() : snapshot.structureName()
		);
		String progressText = String.format(Locale.US, "%.1f", snapshot.progressPercent());
		Component progressLine = Component.translatable(
			"screen.grand_builder.live_progress",
			progressText,
			snapshot.remainingBlocks()
		);
		if (snapshot.modeId() == 0 && estimate != null && estimate.available()) {
			progressLine = Component.translatable("screen.grand_builder.estimate_blocks", estimate.totalBlocks());
		}
		Component etaLine = selectedEffectMode.hidesSpeed()
			? Component.translatable("screen.grand_builder.live_eta_scene", formatEtaTicks(snapshot.etaTicks()))
			: Component.translatable(
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
			return ticks == 0 ? "00:00" : "--:--";
		}

		long totalSeconds = Math.max(1L, (ticks + 19L) / 20L);
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		if (minutes >= 60) return String.format(Locale.US, "%02d:%02d:%02d", minutes/60,minutes%60,seconds);
		return String.format(Locale.US, "%02d:%02d", minutes, seconds);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
