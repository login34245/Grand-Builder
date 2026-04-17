package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.BuildStructure;
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
	private static final int YOUTUBE_BADGE_SIZE = 18;
	private static final int YOUTUBE_TOOLTIP_MAX_WIDTH = 220;
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
	private Button youtubeButton;
	private boolean terrainEnabled = true;
	private int statusPollCooldown = 0;
	private int knownStructureListRevision = -1;
	private int youtubeBadgeLeft;
	private int youtubeBadgeTop;

	public BuilderMenuScreen() {
		super(Component.translatable("screen.grand_builder.title"));
	}

	@Override
	protected void init() {
		PreviewConfirmState.disarm();
		sendControl(BuildControlAction.REQUEST_STRUCTURE_LIST);
		reloadChoices();

		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;
		int halfWidth = (PANEL_WIDTH - 44) / 2;
		int splitRowWidth = PANEL_WIDTH - 44;
		int splitLeft = (splitRowWidth - 4) / 2;
		int splitRight = splitRowWidth - splitLeft - 4;
		int folderButtonWidth = 72;
		int structureButtonWidth = PANEL_WIDTH - 44 - folderButtonWidth - 4;

		this.structureButton = this.addRenderableWidget(Button.builder(structureMessage(), button -> {
			this.selectedStructureIndex = (this.selectedStructureIndex + 1) % this.structureChoices.size();
			this.structureButton.setMessage(structureMessage());
		}).bounds(left + 22, top + 63, structureButtonWidth, 20).build());
		this.folderButton = this.addRenderableWidget(Button.builder(
			Component.translatable("screen.grand_builder.open_structures"),
			button -> openStructuresFolder()
		).bounds(left + 22 + structureButtonWidth + 4, top + 63, folderButtonWidth, 20).build());

		this.speedButton = this.addRenderableWidget(Button.builder(speedMessage(), button -> {
			this.selectedSpeed = this.selectedSpeed.next();
			this.speedButton.setMessage(speedMessage());
			ClientPlayNetworking.send(new BuildSetSpeedPayload(this.selectedSpeed.networkId()));
		}).bounds(left + 22, top + 96, splitLeft, 20).build());
		this.terrainButton = this.addRenderableWidget(Button.builder(terrainMessage(), button -> {
			this.terrainEnabled = !this.terrainEnabled;
			this.terrainButton.setMessage(terrainMessage());
			sendControl(BuildControlAction.TOGGLE_TERRAIN);
		}).bounds(left + 22 + splitLeft + 4, top + 96, splitRight, 20).build());

		this.captureFormatButton = this.addRenderableWidget(Button.builder(captureFormatMessage(), button -> {
			this.selectedCaptureFormat = this.selectedCaptureFormat.next();
			lastCaptureFormat = this.selectedCaptureFormat;
			this.captureFormatButton.setMessage(captureFormatMessage());
		}).bounds(left + 22, top + 123, PANEL_WIDTH - 44, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.start"), button -> startBuild())
			.bounds(left + 22, top + 151, halfWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.capture"), button -> captureCustom())
			.bounds(left + 26 + halfWidth, top + 151, halfWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.pause_resume"), button -> sendControl(BuildControlAction.TOGGLE_PAUSE))
			.bounds(left + 22, top + 178, halfWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.rollback"), button -> sendControl(BuildControlAction.ROLLBACK))
			.bounds(left + 26 + halfWidth, top + 178, halfWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.cancel_preview"), button -> {
			sendControl(BuildControlAction.CANCEL_PREVIEW);
			PreviewConfirmState.disarm();
		}).bounds(left + 22, top + 205, PANEL_WIDTH - 44, 20).build());
		this.youtubeBadgeLeft = left + PANEL_WIDTH + 8;
		this.youtubeBadgeTop = top + 11;
		this.youtubeButton = this.addRenderableWidget(Button.builder(
			Component.translatable("screen.grand_builder.youtube.badge"),
			button -> openYoutubeChannel()
		).bounds(youtubeBadgeLeft, youtubeBadgeTop, YOUTUBE_BADGE_SIZE, YOUTUBE_BADGE_SIZE).build());

		sendControl(BuildControlAction.STATUS_SILENT);
		this.statusPollCooldown = 10;
		YoutubeChannelFeed.forceRefresh();
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
			this.structureButton.setMessage(structureMessage());
		}
	}

	private void syncSpeedFromServer() {
		BuildStatusClientState.Snapshot snapshot = BuildStatusClientState.snapshot();
		BuildSpeed serverSpeed = BuildSpeed.byNetworkId(snapshot.speedId());
		if (serverSpeed != this.selectedSpeed) {
			this.selectedSpeed = serverSpeed;
			if (this.speedButton != null) {
				this.speedButton.setMessage(speedMessage());
			}
		}
		if (snapshot.terrainAdaptationEnabled() != this.terrainEnabled) {
			this.terrainEnabled = snapshot.terrainAdaptationEnabled();
			if (this.terrainButton != null) {
				this.terrainButton.setMessage(terrainMessage());
			}
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(guiGraphics);

		guiGraphics.fillGradient(0, 0, this.width, this.height, 0xCC0E1A2D, 0xCC081018);

		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;

		guiGraphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, 0xF02A4D73);
		guiGraphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0142234, 0xF01B314A);
		guiGraphics.fill(left + 16, top + 47, left + PANEL_WIDTH - 16, top + 48, 0x66B5E8FF);
		guiGraphics.fill(left + 16, top + 116, left + PANEL_WIDTH - 16, top + 117, 0x336A90B5);
		guiGraphics.fill(left + 16, top + 235, left + PANEL_WIDTH - 16, top + 236, 0x33577EA3);

		guiGraphics.drawCenteredString(this.font, Component.translatable("screen.grand_builder.title"), this.width / 2, top + 14, 0xFFF6FAFF);
		guiGraphics.drawCenteredString(this.font, Component.translatable("screen.grand_builder.subtitle"), this.width / 2, top + 28, 0xFFB3D2F0);
		guiGraphics.drawString(this.font, Component.translatable("screen.grand_builder.structure"), left + 24, top + 53, 0xFFDBE9FF);
		guiGraphics.drawString(this.font, Component.translatable("screen.grand_builder.speed"), left + 24, top + 86, 0xFFDBE9FF);
		guiGraphics.drawString(this.font, Component.translatable("screen.grand_builder.capture_format"), left + 24, top + 114, 0xFFDBE9FF);
		renderLiveStatus(guiGraphics, left, top);
		BuildStatusClientState.Snapshot snapshot = BuildStatusClientState.snapshot();
		guiGraphics.drawCenteredString(
			this.font,
			Component.translatable(snapshot.modeId() == 2 ? "screen.grand_builder.hint_preview" : "screen.grand_builder.hint"),
			this.width / 2,
			top + 229,
			0xFF95B6D8
		);

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
		for (String line : rawLines) {
			lines.addAll(wrapLine(line, maxWidth));
		}
		if (lines.isEmpty()) {
			return;
		}

		int width = 0;
		for (String line : lines) {
			width = Math.max(width, this.font.width(line));
		}
		int height = lines.size() * 10 + 6;

		int x = Math.min(startX, this.width - width - 10);
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

	private void renderLiveStatus(GuiGraphics guiGraphics, int left, int top) {
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

		guiGraphics.drawString(this.font, Component.translatable("screen.grand_builder.live_title"), left + 24, top + 245, 0xFFF2F7FF);
		guiGraphics.drawString(this.font, modeText, left + 24, top + 255, 0xFFB9D8F6);
		guiGraphics.drawString(this.font, structureLine, left + 24, top + 265, 0xFF9EC2E6);
		guiGraphics.drawString(this.font, progressLine, left + 24, top + 275, 0xFF9EC2E6);
		guiGraphics.drawString(this.font, etaLine, left + 24, top + 285, 0xFF9EC2E6);
		guiGraphics.drawString(
			this.font,
			Component.translatable(
				snapshot.terrainAdaptationEnabled()
					? "screen.grand_builder.live_terrain_on"
					: "screen.grand_builder.live_terrain_off"
			),
			left + 24,
			top + 295,
			0xFF9EC2E6
		);
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
