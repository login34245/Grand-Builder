package dev.grandbuilder.build;

import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.config.GrandBuilderConfig;
import dev.grandbuilder.network.BuildEffectPayload;
import dev.grandbuilder.network.BuildStatusPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AnimatedBuildManager {
	private static final Map<UUID, BuildSpeed> SPEED_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, BuildEffectMode> EFFECT_MODE_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Boolean> TERRAIN_ADAPTATION_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, String> SELECTION_KEY_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, List<GrandPalaceBlueprint.RelativeBlock>> CUSTOM_BLUEPRINT_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, PendingPreview> PENDING_PREVIEW_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, ArrayDeque<RollbackData>> ROLLBACK_HISTORY_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> LAST_ACTION_TICK_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> PENDING_ROLLBACK_CONFIRM_UNTIL_TICK = new HashMap<>();
	private static final Set<UUID> ONBOARDED_PLAYERS = new HashSet<>();
	private static final List<BuildJob> ACTIVE_BUILDS = new ArrayList<>();
	private static final int TERRAIN_FILL_STAGE_BASE = 2_000_000;
	private static final int TERRAIN_CLEAR_STAGE_BASE = 2_100_000;
	private static final int VEGETATION_CLEAR_STAGE_BASE = 2_200_000;
	private static final int UFO_REVEAL_DELAY_TICKS = 54;
	private static final Comparator<GrandPalaceBlueprint.RelativeBlock> BLOCK_BUILD_ORDER = Comparator
		.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage)
		.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::y)
		.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::x)
		.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::z);

	private AnimatedBuildManager() {
	}

	public static void setSelection(UUID playerId, String selectionKey, BuildSpeed speed, BuildEffectMode effectMode) {
		SELECTION_KEY_BY_PLAYER.put(playerId, selectionKey);
		SPEED_BY_PLAYER.put(playerId, speed);
		EFFECT_MODE_BY_PLAYER.put(playerId, effectMode);
	}

	public static BuildSpeed getSpeed(UUID playerId) {
		return SPEED_BY_PLAYER.getOrDefault(playerId, BuildSpeed.NORMAL);
	}

	public static BuildEffectMode getEffectMode(UUID playerId) {
		return EFFECT_MODE_BY_PLAYER.getOrDefault(playerId, BuildEffectMode.STANDARD);
	}

	public static String getSelectionKey(UUID playerId) {
		return SELECTION_KEY_BY_PLAYER.getOrDefault(playerId, StructureLibrary.defaultSelectionEntry().key());
	}

	public static boolean isTerrainAdaptationEnabled(UUID playerId) {
		return TERRAIN_ADAPTATION_BY_PLAYER.getOrDefault(playerId, GrandBuilderConfig.get().terrainAdaptationEnabled);
	}

	public static void preparePreview(ServerPlayer player) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!checkCanUse(player, true, true) || !allowActionNow(player, false)) {
			return;
		}
		if (findJob(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.already_running"), true);
			return;
		}

		SelectionData selection = resolveSelectedBlueprint(player);
		if (selection == null) {
			return;
		}

		List<GrandPalaceBlueprint.RelativeBlock> blueprint = selection.blueprint();
		if (blueprint.size() > config.maxBlocksPerBuild) {
			player.displayClientMessage(Component.translatable("message.grand_builder.limit_blocks", blueprint.size(), config.maxBlocksPerBuild), true);
			return;
		}
		if (blueprint.size() > config.maxPreviewBlocks) {
			player.displayClientMessage(Component.translatable("message.grand_builder.limit_preview", blueprint.size(), config.maxPreviewBlocks), true);
			return;
		}

		Direction facing = player.getDirection();
		int spawnDistance = clamp(selection.structure().spawnDistance() + config.spawnDistancePadding, config.minSpawnDistance, config.maxSpawnDistance);
		BlockPos origin = player.blockPosition().relative(facing, spawnDistance).below();
		BuildBounds bounds = computeBuildBounds(origin, facing, blueprint);
		if (!fitsWorldHeight(player.level(), bounds)) {
			player.displayClientMessage(Component.translatable("message.grand_builder.bounds_height"), true);
			return;
		}
		if (!isWithinRadius(player.blockPosition(), bounds, config.maxBuildRadius)) {
			player.displayClientMessage(Component.translatable("message.grand_builder.limit_radius", config.maxBuildRadius), true);
			return;
		}

		PendingPreview preview = new PendingPreview(
			player.level(),
			player.level().dimension(),
			origin,
			facing,
			selection.structure().displayName(),
			blueprint,
			getEffectMode(player.getUUID()),
			config.previewSampleCap,
			config
		);
		PENDING_PREVIEW_BY_PLAYER.put(player.getUUID(), preview);

		BuildSpeed speed = getSpeed(player.getUUID());
		long ticksLeft = estimateTicks(preview.totalBlocks(), speed);
		player.displayClientMessage(Component.translatable(
			"message.grand_builder.preview_ready",
			selection.structure().displayName(),
			preview.width(),
			preview.height(),
			preview.depth(),
			preview.totalBlocks(),
			formatDurationTicks(ticksLeft)
		), false);
		player.displayClientMessage(Component.translatable(
			"message.grand_builder.preview_confirm",
			Component.translatable("key.grand_builder.confirm_preview"),
			Component.translatable("key.grand_builder.cancel_preview")
		), true);
		if (preview.effectMode() != BuildEffectMode.STANDARD) {
			player.displayClientMessage(Component.translatable(
				"message.grand_builder.preview_effect",
				Component.translatable(preview.effectMode().translationKey())
			), true);
		}

		if (ONBOARDED_PLAYERS.add(player.getUUID())) {
			player.displayClientMessage(Component.translatable("message.grand_builder.onboarding"), false);
		}

		player.level().playSound(player, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.85f, 1.0f);
	}

	public static void confirmPreview(ServerPlayer player) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!checkCanUse(player, true, true) || !allowActionNow(player, false)) {
			return;
		}
		if (findJob(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.already_running"), true);
			return;
		}

		PendingPreview preview = PENDING_PREVIEW_BY_PLAYER.get(player.getUUID());
		if (preview == null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.preview_missing"), true);
			return;
		}
		if (!preview.dimensionKey.equals(player.level().dimension())) {
			PENDING_PREVIEW_BY_PLAYER.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.grand_builder.preview_dimension_changed"), true);
			return;
		}
		if (config.requireSneakToConfirm && !player.isShiftKeyDown()) {
			player.displayClientMessage(Component.translatable("message.grand_builder.confirm_requires_sneak"), true);
			return;
		}
		if (ACTIVE_BUILDS.size() >= config.maxConcurrentBuilds) {
			player.displayClientMessage(Component.translatable("message.grand_builder.concurrent_limit", config.maxConcurrentBuilds), true);
			return;
		}

		PENDING_PREVIEW_BY_PLAYER.remove(player.getUUID());
		BuildJob job = createBuildJob(player, preview.structureName(), preview.blocks, preview.origin, preview.facing, preview.effectMode());
		if (job == null) {
			return;
		}

		ACTIVE_BUILDS.add(job);
		pushRollback(player.getUUID(), job.rollbackData);
		consumeCoreIfNeeded(player, config);

		BuildSpeed speed = getSpeed(player.getUUID());
		player.displayClientMessage(Component.translatable("message.grand_builder.started", preview.structureName(), speed.displayRate()), true);
		player.level().playSound(player, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.8f);
		if (preview.effectMode() == BuildEffectMode.UFO_INVASION) {
			sendBuildEffect(player, preview.effectMode(), BuildEffectPayload.PHASE_ARRIVAL, UFO_REVEAL_DELAY_TICKS + 16, 1.05f);
		}
	}

	public static void cancelPreview(ServerPlayer player) {
		if (!allowActionNow(player, false)) {
			return;
		}
		if (PENDING_PREVIEW_BY_PLAYER.remove(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.preview_canceled"), true);
		}
	}

	public static void tryStartBuild(ServerPlayer player) {
		if (!checkCanUse(player, true, true)) {
			return;
		}
		if (PENDING_PREVIEW_BY_PLAYER.containsKey(player.getUUID())) {
			confirmPreview(player);
			return;
		}
		preparePreview(player);
	}

	public static void captureCustomStructure(ServerPlayer player) {
		captureCustomStructure(player, CustomCaptureFormat.RUNTIME);
	}

	public static void captureCustomStructure(ServerPlayer player, CustomCaptureFormat format) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!checkCanUse(player, false, true) || !checkCaptureTool(player, config) || !allowActionNow(player, false)) {
			return;
		}

		ServerLevel level = player.level();
		StructureSelectionManager.CompleteSelection selection = StructureSelectionManager.completeSelection(player);
		if (selection == null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.selection_required"), true);
			return;
		}

		CapturedStructure capturedStructure = captureSelectedCuboid(player, selection, config);
		if (capturedStructure == null || capturedStructure.blocks().isEmpty()) {
			return;
		}

		List<GrandPalaceBlueprint.RelativeBlock> captured = new ArrayList<>(capturedStructure.blocks());
		captured.sort(Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage));
		CUSTOM_BLUEPRINT_BY_PLAYER.put(player.getUUID(), captured);
		SELECTION_KEY_BY_PLAYER.put(player.getUUID(), BuildStructure.CUSTOM.selectionKey());
		PENDING_PREVIEW_BY_PLAYER.remove(player.getUUID());

		if (capturedStructure.hitLimit()) {
			player.displayClientMessage(Component.translatable("message.grand_builder.capture_saved_limited", captured.size(), config.maxCaptureBlocks), true);
		} else {
			player.displayClientMessage(Component.translatable(
				"message.grand_builder.capture_saved_detailed",
				captured.size(),
				capturedStructure.width(),
				capturedStructure.height(),
				capturedStructure.depth()
			), true);
		}

		if (format.writesFiles()) {
			try {
				CustomStructureExporter.ExportResult export = CustomStructureExporter.export(player, captured, format);
				StructureLibrary.clearExternalCache();
				player.displayClientMessage(Component.translatable(
					"message.grand_builder.capture_exported",
					Component.translatable(format.translationKey()),
					export.filesWritten(),
					export.path().toAbsolutePath()
				), false);
			} catch (Exception exception) {
				GrandBuilderMod.LOGGER.warn("Failed to export captured structure for {}", player.getName().getString(), exception);
				player.displayClientMessage(Component.translatable("message.grand_builder.capture_export_failed"), true);
			}
		}

		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.75f, 1.1f);
	}

	private static CapturedStructure captureAroundPlayer(ServerPlayer player, GrandBuilderConfig config) {
		ServerLevel level = player.level();
		BlockPos center = player.blockPosition();
		Map<Long, GrandPalaceBlueprint.RelativeBlock> capturedByPos = new HashMap<>();
		boolean hitLimit = false;
		int minDx = Integer.MAX_VALUE;
		int minDy = Integer.MAX_VALUE;
		int minDz = Integer.MAX_VALUE;
		int maxDx = Integer.MIN_VALUE;
		int maxDy = Integer.MIN_VALUE;
		int maxDz = Integer.MIN_VALUE;

		outer:
		for (int dx = -config.captureRadiusXZ; dx <= config.captureRadiusXZ; dx++) {
			for (int dy = -config.captureDown; dy <= config.captureUp; dy++) {
				for (int dz = -config.captureRadiusXZ; dz <= config.captureRadiusXZ; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (shouldSkipNaturalCaptureNoise(level, pos, state)) {
						continue;
					}

					minDx = Math.min(minDx, dx);
					minDy = Math.min(minDy, dy);
					minDz = Math.min(minDz, dz);
					maxDx = Math.max(maxDx, dx);
					maxDy = Math.max(maxDy, dy);
					maxDz = Math.max(maxDz, dz);

					capturedByPos.put(BlockPos.asLong(dx, dy, dz), new GrandPalaceBlueprint.RelativeBlock(
						dx,
						dy,
						dz,
						state,
						stageFor(dx, dy, dz, config.captureDown),
						captureBlockEntityData(level, pos)
					));

					if (capturedByPos.size() >= config.maxCaptureBlocks) {
						hitLimit = true;
						break outer;
					}
				}
			}
		}

		if (capturedByPos.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.grand_builder.capture_empty"), true);
			return null;
		}

		if (!hitLimit) {
			outerAir:
			for (int dx = minDx; dx <= maxDx; dx++) {
				for (int dy = minDy; dy <= maxDy; dy++) {
					for (int dz = minDz; dz <= maxDz; dz++) {
						long relativeKey = BlockPos.asLong(dx, dy, dz);
						if (capturedByPos.containsKey(relativeKey)) {
							continue;
						}
						BlockState state = level.getBlockState(center.offset(dx, dy, dz));
						if (!state.isAir()) {
							continue;
						}
						capturedByPos.put(relativeKey, new GrandPalaceBlueprint.RelativeBlock(
							dx,
							dy,
							dz,
							state,
							stageFor(dx, dy, dz, config.captureDown)
						));
						if (capturedByPos.size() >= config.maxCaptureBlocks) {
							hitLimit = true;
							break outerAir;
						}
					}
				}
			}
		}

		List<GrandPalaceBlueprint.RelativeBlock> captured = new ArrayList<>(capturedByPos.values());
		int width = maxDx - minDx + 1;
		int height = maxDy - minDy + 1;
		int depth = maxDz - minDz + 1;
		return new CapturedStructure(captured, width, height, depth, hitLimit);
	}

	private static CapturedStructure captureSelectedCuboid(
		ServerPlayer player,
		StructureSelectionManager.CompleteSelection selection,
		GrandBuilderConfig config
	) {
		if (selection.volume() > config.maxCaptureBlocks) {
			player.displayClientMessage(Component.translatable(
				"message.grand_builder.selection_too_large",
				selection.volume(),
				config.maxCaptureBlocks
			), true);
			return null;
		}
		if (!areSelectionChunksLoaded(player.level(), selection)) {
			player.displayClientMessage(Component.translatable("message.grand_builder.capture_unloaded_chunks"), true);
			return null;
		}

		ServerLevel level = player.level();
		BlockPos min = selection.min();
		BlockPos max = selection.max();
		int centerX = selection.width() / 2;
		int centerZ = selection.depth() / 2;
		List<GrandPalaceBlueprint.RelativeBlock> captured = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, selection.volume()));
		int solidBlocks = 0;

		for (int y = min.getY(); y <= max.getY(); y++) {
			for (int z = min.getZ(); z <= max.getZ(); z++) {
				for (int x = min.getX(); x <= max.getX(); x++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir()) {
						solidBlocks++;
					}
					int relativeX = (x - min.getX()) - centerX;
					int relativeY = y - min.getY();
					int relativeZ = (z - min.getZ()) - centerZ;
					captured.add(new GrandPalaceBlueprint.RelativeBlock(
						relativeX,
						relativeY,
						relativeZ,
						state,
						stageFor(relativeX, relativeY, relativeZ, 0),
						state.isAir() ? null : captureBlockEntityData(level, pos)
					));
				}
			}
		}

		if (solidBlocks == 0) {
			player.displayClientMessage(Component.translatable("message.grand_builder.capture_empty"), true);
			return null;
		}

		return new CapturedStructure(captured, selection.width(), selection.height(), selection.depth(), false);
	}

	private static boolean areSelectionChunksLoaded(ServerLevel level, StructureSelectionManager.CompleteSelection selection) {
		int minChunkX = selection.min().getX() >> 4;
		int maxChunkX = selection.max().getX() >> 4;
		int minChunkZ = selection.min().getZ() >> 4;
		int maxChunkZ = selection.max().getZ() >> 4;
		int y = selection.min().getY();
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.isLoaded(new BlockPos(chunkX << 4, y, chunkZ << 4))) {
					return false;
				}
			}
		}
		return true;
	}

	public static void sendBuildStatus(ServerPlayer player, boolean showMessage) {
		BuildSpeed speed = getSpeed(player.getUUID());
		boolean terrainEnabled = isTerrainAdaptationEnabled(player.getUUID());
		BuildJob job = findJob(player.getUUID());
		if (job != null) {
			double percent = job.progressPercent();
			String percentText = String.format(Locale.US, "%.1f", percent);
			int remaining = job.remainingBlocks();
			long ticksLeft = estimateTicks(remaining, speed);

			sendStatusPayload(
				player,
				1,
				job.structureName.getString(),
				(float) percent,
				remaining,
				(int) Math.min(Integer.MAX_VALUE, ticksLeft),
				job.paused || job.pausedByOffline,
				speed.networkId(),
				(float) speed.effectiveBlocksPerTick(),
				terrainEnabled
			);

			if (!showMessage) {
				return;
			}
			if (job.pausedByOffline) {
				player.displayClientMessage(Component.translatable("message.grand_builder.status_owner_offline"), true);
				return;
			}
			if (job.paused) {
				player.displayClientMessage(Component.translatable("message.grand_builder.status_paused", job.structureName, percentText, remaining), true);
				return;
			}
			if (job.waitingForChunks) {
				player.displayClientMessage(Component.translatable("message.grand_builder.status_waiting_chunks", job.structureName, percentText, remaining), true);
				return;
			}
			player.displayClientMessage(Component.translatable("message.grand_builder.status_running", job.structureName, percentText, remaining, formatDurationTicks(ticksLeft)), true);
			return;
		}

		PendingPreview preview = PENDING_PREVIEW_BY_PLAYER.get(player.getUUID());
		if (preview != null) {
			long ticksLeft = estimateTicks(preview.totalBlocks(), speed);
			sendStatusPayload(
				player,
				2,
				preview.structureName().getString(),
				0.0f,
				preview.totalBlocks(),
				(int) Math.min(Integer.MAX_VALUE, ticksLeft),
				false,
				speed.networkId(),
				(float) speed.effectiveBlocksPerTick(),
				terrainEnabled
			);
			if (showMessage) {
				player.displayClientMessage(Component.translatable(
					"message.grand_builder.preview_waiting",
					preview.structureName(),
					preview.width(),
					preview.height(),
					preview.depth(),
					preview.totalBlocks(),
					formatDurationTicks(ticksLeft)
				), true);
			}
			return;
		}

		sendStatusPayload(player, 0, "", 0.0f, 0, 0, false, speed.networkId(), (float) speed.effectiveBlocksPerTick(), terrainEnabled);
		if (showMessage) {
			player.displayClientMessage(Component.translatable("message.grand_builder.no_active_build"), true);
		}
	}

	public static void togglePause(ServerPlayer player) {
		if (!allowActionNow(player, false)) {
			return;
		}
		BuildJob job = findJob(player.getUUID());
		if (job == null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.no_active_build"), true);
			return;
		}

		job.paused = !job.paused;
		player.displayClientMessage(Component.translatable(job.paused ? "message.grand_builder.paused" : "message.grand_builder.resumed"), true);
		sendBuildStatus(player, false);
	}

	public static void adjustSpeed(ServerPlayer player, int delta) {
		if (!allowActionNow(player, false)) {
			return;
		}
		BuildSpeed current = getSpeed(player.getUUID());
		BuildSpeed[] speeds = BuildSpeed.values();
		int nextIndex = Math.max(0, Math.min(speeds.length - 1, current.ordinal() + delta));
		setSpeed(player, speeds[nextIndex], true);
	}

	public static void setSpeed(ServerPlayer player, BuildSpeed speed, boolean showMessage) {
		BuildSpeed next = speed == null ? BuildSpeed.NORMAL : speed;
		SPEED_BY_PLAYER.put(player.getUUID(), next);
		if (showMessage) {
			player.displayClientMessage(Component.translatable("message.grand_builder.speed_set", Component.translatable(next.translationKey()), next.displayRate()), true);
		}
		sendBuildStatus(player, false);
	}

	public static void toggleTerrainAdaptation(ServerPlayer player) {
		if (!checkCanUse(player, false, true) || !allowActionNow(player, false)) {
			return;
		}

		UUID playerId = player.getUUID();
		boolean next = !isTerrainAdaptationEnabled(playerId);
		TERRAIN_ADAPTATION_BY_PLAYER.put(playerId, next);
		player.displayClientMessage(Component.translatable(
			next ? "message.grand_builder.terrain_toggle_on" : "message.grand_builder.terrain_toggle_off"
		), true);
		sendBuildStatus(player, false);
	}

	public static void rollbackLastBuild(ServerPlayer player) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!checkCanUse(player, false, true)) {
			return;
		}

		long now = player.level().getGameTime();
		Long confirmUntil = PENDING_ROLLBACK_CONFIRM_UNTIL_TICK.get(player.getUUID());
		boolean confirmed = confirmUntil != null && now <= confirmUntil;
		if (!confirmed && !allowActionNow(player, false)) {
			return;
		}
		if (!confirmed) {
			PENDING_ROLLBACK_CONFIRM_UNTIL_TICK.put(player.getUUID(), now + config.rollbackConfirmWindowTicks);
			if (config.requireSneakForRollback && !player.isShiftKeyDown()) {
				player.displayClientMessage(Component.translatable("message.grand_builder.rollback_confirm_no_sneak"), true);
			} else {
				player.displayClientMessage(Component.translatable("message.grand_builder.rollback_confirm"), true);
			}
			return;
		}

		LAST_ACTION_TICK_BY_PLAYER.put(player.getUUID(), now);
		PENDING_ROLLBACK_CONFIRM_UNTIL_TICK.remove(player.getUUID());
		PENDING_PREVIEW_BY_PLAYER.remove(player.getUUID());

		BuildJob activeJob = removeActiveJob(player.getUUID());
		RollbackData rollback = activeJob != null ? activeJob.rollbackData : popRollback(player.getUUID());
		if (rollback == null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.rollback_missing"), true);
			return;
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.rollback_missing"), true);
			return;
		}

		ServerLevel level = server.getLevel(rollback.dimensionKey);
		if (level == null) {
			player.displayClientMessage(Component.translatable("message.grand_builder.rollback_missing"), true);
			return;
		}

		int restored = 0;
		int skipped = 0;
		for (Map.Entry<BlockPos, SnapshotBlock> entry : rollback.snapshot.entrySet()) {
			BlockPos pos = entry.getKey();
			if (!level.isInWorldBounds(pos)) {
				skipped++;
				continue;
			}
			if (config.pauseWhenChunksMissing && !level.isLoaded(pos)) {
				skipped++;
				continue;
			}

			SnapshotBlock snapshotBlock = entry.getValue();
			level.setBlock(pos, snapshotBlock.state(), 2);
			applyBlockEntityData(level, pos, snapshotBlock.state(), snapshotBlock.blockEntityNbt());
			restored++;
		}

		player.displayClientMessage(Component.translatable("message.grand_builder.rollback_done", rollback.structureName, restored), true);
		if (skipped > 0) {
			player.displayClientMessage(Component.translatable("message.grand_builder.rollback_skipped", skipped), true);
		}
		level.playSound(null, rollback.anchor, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.BLOCKS, 1.0f, 0.95f);
	}

	public static void tick(MinecraftServer server) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		Iterator<BuildJob> iterator = ACTIVE_BUILDS.iterator();
		while (iterator.hasNext()) {
			BuildJob job = iterator.next();
			ServerLevel level = server.getLevel(job.dimensionKey);
			if (level == null) {
				iterator.remove();
				continue;
			}

			ServerPlayer owner = server.getPlayerList().getPlayer(job.ownerId);
			if (config.pauseWhenOwnerOffline && owner == null) {
				job.pausedByOffline = true;
				continue;
			}
			if (owner != null && job.pausedByOffline) {
				job.pausedByOffline = false;
				owner.displayClientMessage(Component.translatable("message.grand_builder.owner_back_resume"), true);
			}

			boolean finished = job.tick(level, owner, getSpeed(job.ownerId), config);
			if (owner != null && (job.statusPulse++ % 20) == 0) {
				sendBuildStatus(owner, false);
			}
			if (owner != null && job.waitingForChunks && (job.statusPulse % 40) == 0) {
				owner.displayClientMessage(Component.translatable("message.grand_builder.waiting_chunks"), true);
			}

			if (finished) {
				job.finishEffects(level);
				if (owner != null) {
					owner.displayClientMessage(Component.translatable("message.grand_builder.completed", job.structureName), true);
					if (job.skippedBlocks > 0) {
						owner.displayClientMessage(Component.translatable("message.grand_builder.completed_with_skips", job.skippedBlocks), true);
					}
				}
				level.playSound(null, job.origin, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
				iterator.remove();
			}
		}

		Iterator<Map.Entry<UUID, PendingPreview>> previewIterator = PENDING_PREVIEW_BY_PLAYER.entrySet().iterator();
		while (previewIterator.hasNext()) {
			Map.Entry<UUID, PendingPreview> entry = previewIterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null) {
				previewIterator.remove();
				continue;
			}
			if (!isHoldingStructureCore(owner)) {
				previewIterator.remove();
				owner.displayClientMessage(Component.translatable("message.grand_builder.preview_canceled_hand"), true);
				continue;
			}

			PendingPreview preview = entry.getValue();
			if (!preview.dimensionKey.equals(owner.level().dimension())) {
				previewIterator.remove();
				owner.displayClientMessage(Component.translatable("message.grand_builder.preview_dimension_changed"), true);
				continue;
			}

			ServerLevel level = server.getLevel(preview.dimensionKey);
			if (level != null) {
				preview.tick(level, config);
			}
		}
	}

	public static void onPlayerDisconnect(ServerPlayer player) {
		UUID playerId = player.getUUID();
		PENDING_PREVIEW_BY_PLAYER.remove(playerId);
		PENDING_ROLLBACK_CONFIRM_UNTIL_TICK.remove(playerId);
		LAST_ACTION_TICK_BY_PLAYER.remove(playerId);
		TERRAIN_ADAPTATION_BY_PLAYER.remove(playerId);
		EFFECT_MODE_BY_PLAYER.remove(playerId);
	}

	public static void shutdown() {
		ACTIVE_BUILDS.clear();
		PENDING_PREVIEW_BY_PLAYER.clear();
		PENDING_ROLLBACK_CONFIRM_UNTIL_TICK.clear();
		LAST_ACTION_TICK_BY_PLAYER.clear();
		TERRAIN_ADAPTATION_BY_PLAYER.clear();
		EFFECT_MODE_BY_PLAYER.clear();
		GrandBuilderMod.LOGGER.info("Grand Builder runtime state cleared");
	}

	public static String speedKey(BuildSpeed speed) {
		return speed.translationKey();
	}

	private static boolean checkCanUse(ServerPlayer player, boolean requireCore, boolean showMessage) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!config.hasPermission(player)) {
			if (showMessage) {
				player.displayClientMessage(Component.translatable("message.grand_builder.no_permission"), true);
			}
			return false;
		}
		if (!config.isDimensionAllowed(player.level().dimension())) {
			if (showMessage) {
				player.displayClientMessage(Component.translatable("message.grand_builder.dimension_blocked"), true);
			}
			return false;
		}
		if (!requireCore) {
			return true;
		}

		boolean needsCore = !player.isCreative() || !config.allowCreativeWithoutCore;
		if (needsCore && config.requireStructureCoreInSurvival && !isHoldingStructureCore(player)) {
			if (showMessage) {
				player.displayClientMessage(Component.translatable("message.grand_builder.core_required"), true);
			}
			return false;
		}
		return true;
	}

	private static boolean allowActionNow(ServerPlayer player, boolean silent) {
		int cooldown = GrandBuilderConfig.get().actionCooldownTicks;
		if (cooldown <= 0) {
			return true;
		}

		long now = player.level().getGameTime();
		long last = LAST_ACTION_TICK_BY_PLAYER.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
		if (now - last < cooldown) {
			if (!silent) {
				player.displayClientMessage(Component.translatable("message.grand_builder.cooldown"), true);
			}
			return false;
		}

		LAST_ACTION_TICK_BY_PLAYER.put(player.getUUID(), now);
		return true;
	}

	private static void consumeCoreIfNeeded(ServerPlayer player, GrandBuilderConfig config) {
		if (!config.consumeStructureCoreInSurvival || player.isCreative() || !isHoldingStructureCore(player)) {
			return;
		}
		if (player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_CORE)) {
			player.getMainHandItem().shrink(1);
		} else if (player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_CORE)) {
			player.getOffhandItem().shrink(1);
		}
	}

	private static boolean isHoldingStructureCore(ServerPlayer player) {
		return player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_CORE) || player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_CORE);
	}

	private static boolean checkCaptureTool(ServerPlayer player, GrandBuilderConfig config) {
		if (player.isCreative() && config.allowCreativeWithoutCore) {
			return true;
		}
		if (player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_CORE)
			|| player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_CORE)
			|| player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_SELECTOR)
			|| player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_SELECTOR)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.grand_builder.capture_tool_required"), true);
		return false;
	}

	private static SelectionData resolveSelectedBlueprint(ServerPlayer player) {
		StructureLibrary.ResolvedStructure resolved = StructureLibrary.resolveSelection(getSelectionKey(player.getUUID()));

		List<GrandPalaceBlueprint.RelativeBlock> blueprint;
		if (resolved.custom()) {
			blueprint = CUSTOM_BLUEPRINT_BY_PLAYER.get(player.getUUID());
			if (blueprint == null || blueprint.isEmpty()) {
				player.displayClientMessage(Component.translatable("message.grand_builder.custom_missing"), true);
				return null;
			}
		} else {
			blueprint = resolved.blueprint();
		}

		if (blueprint == null || blueprint.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.grand_builder.custom_missing"), true);
			return null;
		}
		return new SelectionData(resolved, new ArrayList<>(blueprint));
	}

	private static BuildJob createBuildJob(
		ServerPlayer player,
		Component structureName,
		List<GrandPalaceBlueprint.RelativeBlock> blueprint,
		BlockPos origin,
		Direction facing,
		BuildEffectMode effectMode
	) {
		if (blueprint.isEmpty()) {
			return null;
		}

		GrandBuilderConfig config = GrandBuilderConfig.get();
		List<GrandPalaceBlueprint.RelativeBlock> finalBlueprint = new ArrayList<>(blueprint);
		int terrainBlocksAdded = 0;
		if (isTerrainAdaptationEnabled(player.getUUID())) {
			List<GrandPalaceBlueprint.RelativeBlock> terrainBlocks = generateTerrainAdaptationBlocks(player.level(), finalBlueprint, origin, facing, config);
			terrainBlocksAdded = terrainBlocks.size();
			if (!terrainBlocks.isEmpty()) {
				finalBlueprint = mergeBlueprintBlocks(finalBlueprint, terrainBlocks);
			}
		}

		finalBlueprint.sort(BLOCK_BUILD_ORDER);
		if (finalBlueprint.size() > config.maxBlocksPerBuild) {
			player.displayClientMessage(Component.translatable("message.grand_builder.limit_blocks", finalBlueprint.size(), config.maxBlocksPerBuild), true);
			return null;
		}
		if (terrainBlocksAdded > 0) {
			player.displayClientMessage(Component.translatable("message.grand_builder.terrain_prepared", terrainBlocksAdded), true);
		}

		BuildBounds effectBounds = computeBuildBounds(origin, facing, finalBlueprint);
		RollbackData rollbackData = captureRollbackSnapshot(player.level(), player.level().dimension(), structureName, origin, facing, finalBlueprint);
		return new BuildJob(player.level().dimension(), origin, facing, player.getUUID(), structureName, finalBlueprint, rollbackData, effectMode, effectBounds);
	}

	private static List<GrandPalaceBlueprint.RelativeBlock> mergeBlueprintBlocks(
		List<GrandPalaceBlueprint.RelativeBlock> structureBlocks,
		List<GrandPalaceBlueprint.RelativeBlock> terrainBlocks
	) {
		Map<Long, GrandPalaceBlueprint.RelativeBlock> merged = new HashMap<>(structureBlocks.size() + terrainBlocks.size());
		for (GrandPalaceBlueprint.RelativeBlock block : terrainBlocks) {
			merged.put(BlockPos.asLong(block.x(), block.y(), block.z()), block);
		}
		for (GrandPalaceBlueprint.RelativeBlock block : structureBlocks) {
			merged.put(BlockPos.asLong(block.x(), block.y(), block.z()), block);
		}

		List<GrandPalaceBlueprint.RelativeBlock> mergedList = new ArrayList<>(merged.values());
		mergedList.sort(BLOCK_BUILD_ORDER);
		return mergedList;
	}

	private static List<GrandPalaceBlueprint.RelativeBlock> generateTerrainAdaptationBlocks(
		ServerLevel level,
		List<GrandPalaceBlueprint.RelativeBlock> structureBlocks,
		BlockPos origin,
		Direction facing,
		GrandBuilderConfig config
	) {
		if (structureBlocks.isEmpty()) {
			return List.of();
		}

		Map<Long, ColumnInfo> structureColumns = new HashMap<>();
		Set<Long> occupiedWorldPositions = new HashSet<>(structureBlocks.size() * 2);
		int globalMinY = Integer.MAX_VALUE;
		int globalMaxY = Integer.MIN_VALUE;
		int boundsMinX = Integer.MAX_VALUE;
		int boundsMaxX = Integer.MIN_VALUE;
		int boundsMinZ = Integer.MAX_VALUE;
		int boundsMaxZ = Integer.MIN_VALUE;

		for (GrandPalaceBlueprint.RelativeBlock block : structureBlocks) {
			if (block.state().isAir()) {
				continue;
			}
			BlockPos worldPos = transform(origin, facing, block);
			globalMinY = Math.min(globalMinY, worldPos.getY());
			globalMaxY = Math.max(globalMaxY, worldPos.getY());
			boundsMinX = Math.min(boundsMinX, worldPos.getX());
			boundsMaxX = Math.max(boundsMaxX, worldPos.getX());
			boundsMinZ = Math.min(boundsMinZ, worldPos.getZ());
			boundsMaxZ = Math.max(boundsMaxZ, worldPos.getZ());

			structureColumns.computeIfAbsent(xzKey(worldPos.getX(), worldPos.getZ()), ignored -> new ColumnInfo(worldPos.getY()))
				.observe(worldPos.getY());
			occupiedWorldPositions.add(worldPos.asLong());
		}
		if (structureColumns.isEmpty() || globalMinY == Integer.MAX_VALUE) {
			return List.of();
		}

		Map<Long, Integer> adaptationColumns = collectAdaptationColumns(structureColumns, globalMinY, Math.max(0, config.terrainAdaptMargin));
		adaptationColumns = applyTerrainSkirt(adaptationColumns, Math.max(0, config.terrainSkirtRadius));
		if (adaptationColumns.isEmpty()) {
			return List.of();
		}

		BlockState fallbackFoundationState = resolveTerrainFoundation(config.terrainFoundationBlock);
		int margin = Math.max(0, config.terrainAdaptMargin);
		int fillDepth = Math.max(1, config.terrainAdaptMaxFillDepth);
		int cutHeight = Math.max(0, config.terrainAdaptMaxCutHeight);

		Map<Long, GrandPalaceBlueprint.RelativeBlock> terrainOps = new HashMap<>();
		for (Map.Entry<Long, Integer> adaptationColumn : adaptationColumns.entrySet()) {
			int x = xFromKey(adaptationColumn.getKey());
			int z = zFromKey(adaptationColumn.getKey());
			int baseY = adaptationColumn.getValue();
			if (!isColumnSafeForTerrainAdaptation(level, x, z, baseY, cutHeight, margin)) {
				continue;
			}

			TerrainPalette palette = sampleTerrainPalette(level, x, z, baseY, fallbackFoundationState);
			for (int depth = 0; depth < fillDepth; depth++) {
				int y = baseY - depth;
				BlockPos pos = new BlockPos(x, y, z);
				if (!level.isInWorldBounds(pos)) {
					break;
				}
				BlockState existing = level.getBlockState(pos);
				if (!needsTerrainFill(existing)) {
					break;
				}

				BlockState nextState = depth == 0 ? palette.topState() : palette.fillState();
				if (depth == 0 && !level.canSeeSky(pos.above())) {
					nextState = palette.fillState();
				}
				int stage = TERRAIN_FILL_STAGE_BASE + depth;
				terrainOps.put(pos.asLong(), toRelativeBlock(origin, facing, pos, nextState, stage));
			}

			for (int up = 1; up <= cutHeight; up++) {
				int y = baseY + up;
				BlockPos pos = new BlockPos(x, y, z);
				if (!level.isInWorldBounds(pos)) {
					break;
				}
				long posKey = pos.asLong();
				if (occupiedWorldPositions.contains(posKey)) {
					continue;
				}
				BlockState existing = level.getBlockState(pos);
				if (!shouldClearForTerrain(existing, level, pos)) {
					continue;
				}
				int stage = TERRAIN_CLEAR_STAGE_BASE + up;
				terrainOps.put(posKey, toRelativeBlock(origin, facing, pos, Blocks.AIR.defaultBlockState(), stage));
			}
		}
		if (config.terrainClearNearbyTrees && config.terrainTreeClearRadius > 0 && config.terrainTreeClearHeight > 0
			&& globalMaxY != Integer.MIN_VALUE && boundsMinX != Integer.MAX_VALUE) {
			addNearbyVegetationClearOps(
				level,
				terrainOps,
				occupiedWorldPositions,
				origin,
				facing,
				boundsMinX,
				globalMinY,
				boundsMinZ,
				boundsMaxX,
				globalMaxY,
				boundsMaxZ,
				config.terrainTreeClearRadius,
				config.terrainTreeClearHeight
			);
		}

		List<GrandPalaceBlueprint.RelativeBlock> result = new ArrayList<>(terrainOps.values());
		result.sort(BLOCK_BUILD_ORDER);
		return result;
	}

	private static Map<Long, Integer> collectAdaptationColumns(Map<Long, ColumnInfo> structureColumns, int globalMinY, int margin) {
		final int supportTolerance = 4;
		Map<Long, Integer> adaptColumns = new HashMap<>();

		for (Map.Entry<Long, ColumnInfo> entry : structureColumns.entrySet()) {
			ColumnInfo column = entry.getValue();
			if (column.minY > globalMinY + supportTolerance) {
				continue;
			}

			int originX = xFromKey(entry.getKey());
			int originZ = zFromKey(entry.getKey());
			int supportBaseY = column.minY - 1;
			for (int dx = -margin; dx <= margin; dx++) {
				for (int dz = -margin; dz <= margin; dz++) {
					int distance = Math.max(Math.abs(dx), Math.abs(dz));
					int adaptedBaseY = supportBaseY - distance;
					long key = xzKey(originX + dx, originZ + dz);
					adaptColumns.merge(key, adaptedBaseY, Math::max);
				}
			}
		}

		return adaptColumns;
	}

	private static Map<Long, Integer> applyTerrainSkirt(Map<Long, Integer> baseColumns, int skirtRadius) {
		if (baseColumns.isEmpty() || skirtRadius <= 0) {
			return baseColumns;
		}

		Map<Long, Integer> expanded = new HashMap<>(baseColumns);
		for (Map.Entry<Long, Integer> entry : baseColumns.entrySet()) {
			int x = xFromKey(entry.getKey());
			int z = zFromKey(entry.getKey());
			int baseY = entry.getValue();
			for (int dx = -skirtRadius; dx <= skirtRadius; dx++) {
				for (int dz = -skirtRadius; dz <= skirtRadius; dz++) {
					int distance = Math.max(Math.abs(dx), Math.abs(dz));
					if (distance == 0 || distance > skirtRadius) {
						continue;
					}
					long key = xzKey(x + dx, z + dz);
					int smoothedY = baseY - distance;
					expanded.merge(key, smoothedY, Math::max);
				}
			}
		}
		return expanded;
	}

	private static void addNearbyVegetationClearOps(
		ServerLevel level,
		Map<Long, GrandPalaceBlueprint.RelativeBlock> terrainOps,
		Set<Long> occupiedWorldPositions,
		BlockPos origin,
		Direction facing,
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ,
		int clearRadius,
		int clearHeight
	) {
		int fromX = minX - clearRadius;
		int toX = maxX + clearRadius;
		int fromZ = minZ - clearRadius;
		int toZ = maxZ + clearRadius;
		int fromY = Math.max(level.getMinY(), minY - 2);
		int toY = Math.min(level.getMaxY() - 1, maxY + clearHeight);

		for (int x = fromX; x <= toX; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				for (int y = fromY; y <= toY; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.isInWorldBounds(pos)) {
						continue;
					}
					long posKey = pos.asLong();
					if (occupiedWorldPositions.contains(posKey)) {
						continue;
					}
					GrandPalaceBlueprint.RelativeBlock pending = terrainOps.get(posKey);
					if (pending != null && !pending.state().isAir()) {
						continue;
					}

					BlockState existing = level.getBlockState(pos);
					if (!shouldClearNearbyVegetation(level, pos, existing)) {
						continue;
					}
					int stage = VEGETATION_CLEAR_STAGE_BASE + Math.max(0, y - fromY);
					terrainOps.put(posKey, toRelativeBlock(origin, facing, pos, Blocks.AIR.defaultBlockState(), stage));
				}
			}
		}
	}

	private static TerrainPalette sampleTerrainPalette(ServerLevel level, int x, int z, int baseY, BlockState fallbackFoundationState) {
		BlockState top = fallbackFoundationState;
		BlockState fill = fallbackFoundationState;
		int bestDistance = Integer.MAX_VALUE;

		for (int radius = 0; radius <= 3; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					BlockState sampled = findNearbySurface(level, x + dx, z + dz, baseY);
					if (sampled == null) {
						continue;
					}

					int distance = Math.abs(dx) + Math.abs(dz);
					if (distance > bestDistance) {
						continue;
					}
					bestDistance = distance;
					top = normalizeTopTerrainState(sampled, fallbackFoundationState);
					fill = inferFillTerrainState(top, fallbackFoundationState);
				}
			}
		}

		return new TerrainPalette(top, fill);
	}

	private static BlockState findNearbySurface(ServerLevel level, int x, int z, int baseY) {
		int minY = Math.max(level.getMinY(), baseY - 10);
		int maxY = Math.min(level.getMaxY() - 1, baseY + 8);
		for (int y = maxY; y >= minY; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) {
				continue;
			}
			if (!isNaturalTerrainState(level, pos, state)) {
				continue;
			}
			return state;
		}
		return null;
	}

	private static BlockState normalizeTopTerrainState(BlockState sampled, BlockState fallbackFoundationState) {
		if (sampled.is(Blocks.GRASS_BLOCK) || sampled.is(Blocks.DIRT) || sampled.is(Blocks.PODZOL) || sampled.is(Blocks.MYCELIUM)
			|| sampled.is(Blocks.COARSE_DIRT) || sampled.is(Blocks.ROOTED_DIRT) || sampled.is(Blocks.MOSS_BLOCK)
			|| sampled.is(Blocks.MUD) || sampled.is(Blocks.SAND) || sampled.is(Blocks.RED_SAND) || sampled.is(Blocks.GRAVEL)
			|| sampled.is(Blocks.CLAY) || sampled.is(Blocks.SNOW_BLOCK)) {
			return sampled;
		}

		if (sampled.is(Blocks.STONE) || sampled.is(Blocks.ANDESITE) || sampled.is(Blocks.DIORITE) || sampled.is(Blocks.GRANITE)
			|| sampled.is(Blocks.TUFF) || sampled.is(Blocks.DEEPSLATE)) {
			return Blocks.STONE.defaultBlockState();
		}

		return fallbackFoundationState;
	}

	private static BlockState inferFillTerrainState(BlockState top, BlockState fallbackFoundationState) {
		if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.COARSE_DIRT)
			|| top.is(Blocks.ROOTED_DIRT) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.DIRT_PATH)) {
			return Blocks.DIRT.defaultBlockState();
		}
		if (top.is(Blocks.MUD)) {
			return Blocks.PACKED_MUD.defaultBlockState();
		}
		if (top.is(Blocks.SAND) || top.is(Blocks.RED_SAND) || top.is(Blocks.GRAVEL) || top.is(Blocks.CLAY) || top.is(Blocks.SNOW_BLOCK)) {
			return top;
		}
		if (top.is(Blocks.DIRT)) {
			return top;
		}
		return fallbackFoundationState;
	}

	private static boolean needsTerrainFill(BlockState existing) {
		return existing.isAir() || existing.canBeReplaced() || !existing.getFluidState().isEmpty();
	}

	private static boolean shouldClearForTerrain(BlockState existing, ServerLevel level, BlockPos pos) {
		if (existing.isAir() || existing.hasBlockEntity()) {
			return false;
		}
		if (!isNaturalTerrainState(level, pos, existing)) {
			return false;
		}
		if (!level.canSeeSky(pos.above())) {
			return false;
		}
		float hardness = existing.getDestroySpeed(level, pos);
		return hardness >= 0.0f;
	}

	private static boolean isColumnSafeForTerrainAdaptation(ServerLevel level, int x, int z, int baseY, int cutHeight, int margin) {
		int minY = Math.max(level.getMinY(), baseY - 1);
		int maxY = Math.min(level.getMaxY() - 1, baseY + Math.max(2, cutHeight + margin + 2));
		for (int y = minY; y <= maxY; y++) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) {
				continue;
			}
			if (!isNaturalTerrainState(level, pos, state)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isNaturalTerrainState(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) {
			return true;
		}
		if (state.hasBlockEntity()) {
			return false;
		}

		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
			|| state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD)
			|| state.is(Blocks.PACKED_MUD) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.CLAY) || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE)
			|| state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE) || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF)
			|| state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE)
			|| state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.TERRACOTTA)
			|| state.is(Blocks.RED_TERRACOTTA) || state.is(Blocks.ORANGE_TERRACOTTA) || state.is(Blocks.YELLOW_TERRACOTTA)
			|| state.is(Blocks.BROWN_TERRACOTTA) || state.is(Blocks.WHITE_TERRACOTTA) || state.is(Blocks.LIGHT_GRAY_TERRACOTTA)
			|| state.is(Blocks.GRAY_TERRACOTTA) || state.is(Blocks.BLACK_TERRACOTTA) || state.is(Blocks.CYAN_TERRACOTTA)
			|| state.is(Blocks.LIGHT_BLUE_TERRACOTTA) || state.is(Blocks.BLUE_TERRACOTTA) || state.is(Blocks.PURPLE_TERRACOTTA)
			|| state.is(Blocks.MAGENTA_TERRACOTTA) || state.is(Blocks.PINK_TERRACOTTA) || state.is(Blocks.LIME_TERRACOTTA)
			|| state.is(Blocks.GREEN_TERRACOTTA)) {
			return true;
		}

		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (blockId == null) {
			return false;
		}
		String path = blockId.getPath();
		if (path.isEmpty()) {
			return false;
		}
		if (path.contains("planks") || path.contains("brick") || path.contains("slab") || path.contains("stairs")
			|| path.contains("wall") || path.contains("fence") || path.contains("door") || path.contains("trapdoor")
			|| path.contains("glass") || path.contains("pane") || path.contains("wool") || path.contains("concrete")
			|| path.contains("copper") || path.contains("gold") || path.contains("iron") || path.contains("diamond")
			|| path.contains("quartz") || path.contains("purpur") || path.contains("smooth_") || path.contains("chiseled")
			|| path.contains("lamp") || path.contains("lantern") || path.contains("torch") || path.contains("crafting")
			|| path.contains("barrel") || path.contains("chest") || path.contains("bookshelf") || path.contains("lectern")
			|| path.contains("bed") || path.contains("banner") || path.contains("mosaic")) {
			return false;
		}
		return path.contains("dirt") || path.contains("grass") || path.contains("stone") || path.contains("deepslate")
			|| path.contains("sand") || path.contains("gravel") || path.contains("clay") || path.contains("terracotta")
			|| path.contains("mud") || path.contains("snow") || path.contains("ice") || path.contains("tuff")
			|| path.contains("calcite") || path.contains("dripstone");
	}

	private static boolean isNaturalBuildObstacle(ServerLevel level, BlockPos pos, BlockState state) {
		if (isNaturalTerrainState(level, pos, state)) {
			return true;
		}
		if (state.hasBlockEntity()) {
			return false;
		}
		if (state.is(BlockTags.LEAVES)) {
			return true;
		}
		if (state.is(BlockTags.LOGS)) {
			return isLikelyTreeLog(level, pos, state);
		}
		return isOreBlock(state);
	}

	private static boolean shouldClearNearbyVegetation(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity()) {
			return false;
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)) {
			return true;
		}
		if (state.is(BlockTags.LOGS)) {
			return isLikelyTreeLog(level, pos, state);
		}
		if (state.canBeReplaced() || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.HANGING_ROOTS)) {
			return level.canSeeSky(pos.above());
		}
		return false;
	}

	private static boolean isLikelyTreeLog(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.is(BlockTags.LOGS)) {
			return false;
		}
		if (hasNearbyLeaves(level, pos, 4)) {
			return true;
		}
		return level.canSeeSky(pos.above());
	}

	private static boolean hasNearbyLeaves(ServerLevel level, BlockPos center, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > radius + 1) {
						continue;
					}
					BlockPos nearby = center.offset(dx, dy, dz);
					if (!level.isInWorldBounds(nearby)) {
						continue;
					}
					if (level.getBlockState(nearby).is(BlockTags.LEAVES)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean shouldSkipNaturalCaptureNoise(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.hasBlockEntity()) {
			return false;
		}
		if (isOreBlock(state)) {
			return true;
		}
		if (state.is(BlockTags.LEAVES)) {
			return level.canSeeSky(pos.above());
		}
		if (state.is(BlockTags.LOGS)) {
			return isLikelyTreeLog(level, pos, state);
		}
		return false;
	}

	private static boolean isOreBlock(BlockState state) {
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (blockId == null) {
			return false;
		}
		String path = blockId.getPath();
		return path.contains("_ore");
	}

	private static boolean canReplace(
		ServerLevel level,
		BlockPos pos,
		BlockState existing,
		BlockState target,
		GrandBuilderConfig.ReplaceRule replaceRule
	) {
		if (existing.equals(target)) {
			return true;
		}
		if (target.isAir()) {
			if (existing.hasBlockEntity()) {
				return replaceRule == GrandBuilderConfig.ReplaceRule.ALL;
			}
			return switch (replaceRule) {
				case AIR_ONLY -> existing.isAir() || existing.canBeReplaced();
				case REPLACEABLE -> existing.isAir() || existing.canBeReplaced() || isNaturalBuildObstacle(level, pos, existing);
				case ALL -> true;
			};
		}
		return switch (replaceRule) {
			case AIR_ONLY -> existing.isAir();
			case REPLACEABLE -> existing.isAir() || existing.canBeReplaced() || isNaturalBuildObstacle(level, pos, existing);
			case ALL -> true;
		};
	}

	private static BlockState resolveTerrainFoundation(String blockId) {
		Identifier id = Identifier.tryParse(blockId);
		if (id == null) {
			return Blocks.STONE.defaultBlockState();
		}
		return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.STONE).defaultBlockState();
	}

	private static long xzKey(int x, int z) {
		return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static int xFromKey(long key) {
		return (int) (key >> 32);
	}

	private static int zFromKey(long key) {
		return (int) key;
	}

	private static GrandPalaceBlueprint.RelativeBlock toRelativeBlock(
		BlockPos origin,
		Direction facing,
		BlockPos worldPos,
		BlockState state,
		int stage
	) {
		int dx = worldPos.getX() - origin.getX();
		int dz = worldPos.getZ() - origin.getZ();
		int relativeX;
		int relativeZ;
		switch (facing) {
			case EAST -> {
				relativeX = dz;
				relativeZ = -dx;
			}
			case SOUTH -> {
				relativeX = -dx;
				relativeZ = -dz;
			}
			case WEST -> {
				relativeX = -dz;
				relativeZ = dx;
			}
			default -> {
				relativeX = dx;
				relativeZ = dz;
			}
		}
		return new GrandPalaceBlueprint.RelativeBlock(relativeX, worldPos.getY() - origin.getY(), relativeZ, state, stage);
	}

	private static RollbackData captureRollbackSnapshot(
		ServerLevel level,
		ResourceKey<Level> dimensionKey,
		Component structureName,
		BlockPos origin,
		Direction facing,
		List<GrandPalaceBlueprint.RelativeBlock> blocks
	) {
		Map<BlockPos, SnapshotBlock> snapshot = new HashMap<>();
		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			BlockPos targetPos = transform(origin, facing, block);
			if (!level.isInWorldBounds(targetPos) || !level.isLoaded(targetPos)) {
				continue;
			}
			BlockPos immutablePos = targetPos.immutable();
			if (snapshot.containsKey(immutablePos)) {
				continue;
			}
			BlockState state = level.getBlockState(immutablePos);
			BlockState targetState = rotateState(block.state(), facing);
			if (state.equals(targetState)) {
				continue;
			}
			snapshot.put(immutablePos, new SnapshotBlock(state, captureBlockEntityData(level, immutablePos)));
		}
		return new RollbackData(dimensionKey, structureName, origin, snapshot);
	}

	private static void pushRollback(UUID playerId, RollbackData rollbackData) {
		ArrayDeque<RollbackData> queue = ROLLBACK_HISTORY_BY_PLAYER.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
		queue.addLast(rollbackData);
		while (queue.size() > GrandBuilderConfig.get().maxRollbackHistoryPerPlayer) {
			queue.removeFirst();
		}
	}

	private static RollbackData popRollback(UUID playerId) {
		ArrayDeque<RollbackData> queue = ROLLBACK_HISTORY_BY_PLAYER.get(playerId);
		return (queue == null || queue.isEmpty()) ? null : queue.pollLast();
	}

	private static int stageFor(int x, int y, int z, int captureDown) {
		int wave = Math.floorMod(x * 5 - z * 3, 11);
		int radial = (int) Math.round(Math.sqrt(x * x + z * z) * 8);
		return (y + captureDown) * 120 + radial + wave;
	}

	private static String formatDurationTicks(long ticks) {
		if (ticks < 0) {
			return "--";
		}
		long seconds = Math.max(1L, (ticks + 19L) / 20L);
		long minutes = seconds / 60L;
		long sec = seconds % 60L;
		return String.format(Locale.US, "%02d:%02d", minutes, sec);
	}

	private static long estimateTicks(int remainingBlocks, BuildSpeed speed) {
		if (remainingBlocks <= 0) {
			return 0L;
		}
		double ticks = (remainingBlocks * (double) speed.tickDelay()) / speed.blocksPerCycle();
		return Math.max(1L, (long) Math.ceil(ticks));
	}

	private static void sendStatusPayload(
		ServerPlayer player,
		int modeId,
		String structureName,
		float progressPercent,
		int remainingBlocks,
		int etaTicks,
		boolean paused,
		int speedId,
		float speedBlocksPerTick,
		boolean terrainAdaptationEnabled
	) {
		ServerPlayNetworking.send(player, new BuildStatusPayload(
			modeId,
			structureName,
			progressPercent,
			remainingBlocks,
			etaTicks,
			paused,
			speedId,
			speedBlocksPerTick,
			terrainAdaptationEnabled
		));
	}

	private static void sendBuildEffect(ServerPlayer player, BuildEffectMode effectMode, int phaseId, int durationTicks, float intensity) {
		ServerPlayNetworking.send(player, new BuildEffectPayload(effectMode.networkId(), phaseId, durationTicks, intensity));
	}

	private static BuildJob findJob(UUID playerId) {
		for (BuildJob job : ACTIVE_BUILDS) {
			if (job.ownerId.equals(playerId)) {
				return job;
			}
		}
		return null;
	}

	private static BuildJob removeActiveJob(UUID playerId) {
		Iterator<BuildJob> iterator = ACTIVE_BUILDS.iterator();
		while (iterator.hasNext()) {
			BuildJob job = iterator.next();
			if (job.ownerId.equals(playerId)) {
				iterator.remove();
				return job;
			}
		}
		return null;
	}

	private static boolean fitsWorldHeight(ServerLevel level, BuildBounds bounds) {
		return bounds.minY() >= level.getMinY() && bounds.maxY() < level.getMaxY();
	}

	private static boolean isWithinRadius(BlockPos playerPos, BuildBounds bounds, int maxRadius) {
		int farX = Math.max(Math.abs(bounds.minX() - playerPos.getX()), Math.abs(bounds.maxX() - playerPos.getX()));
		int farZ = Math.max(Math.abs(bounds.minZ() - playerPos.getZ()), Math.abs(bounds.maxZ() - playerPos.getZ()));
		double distance = Math.sqrt((farX * (double) farX) + (farZ * (double) farZ));
		return distance <= maxRadius;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Rotation rotationForFacing(Direction facing) {
		return switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	private static BlockState rotateState(BlockState state, Direction facing) {
		return state.rotate(rotationForFacing(facing));
	}

	private static BlockPos transform(BlockPos origin, Direction facing, GrandPalaceBlueprint.RelativeBlock block) {
		int x = block.x();
		int z = block.z();
		int rotatedX;
		int rotatedZ;
		switch (facing) {
			case EAST -> {
				rotatedX = -z;
				rotatedZ = x;
			}
			case SOUTH -> {
				rotatedX = -x;
				rotatedZ = -z;
			}
			case WEST -> {
				rotatedX = z;
				rotatedZ = -x;
			}
			default -> {
				rotatedX = x;
				rotatedZ = z;
			}
		}
		return origin.offset(rotatedX, block.y(), rotatedZ);
	}

	private static BuildBounds computeBuildBounds(BlockPos anchor, Direction facing, List<GrandPalaceBlueprint.RelativeBlock> blocks) {
		if (blocks.isEmpty()) {
			return new BuildBounds(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX(), anchor.getY(), anchor.getZ(), anchor.immutable(), facing);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			BlockPos transformed = transform(anchor, facing, block);
			minX = Math.min(minX, transformed.getX());
			minY = Math.min(minY, transformed.getY());
			minZ = Math.min(minZ, transformed.getZ());
			maxX = Math.max(maxX, transformed.getX());
			maxY = Math.max(maxY, transformed.getY());
			maxZ = Math.max(maxZ, transformed.getZ());
		}

		return new BuildBounds(minX, minY, minZ, maxX, maxY, maxZ, anchor.immutable(), facing);
	}

	private record SelectionData(StructureLibrary.ResolvedStructure structure, List<GrandPalaceBlueprint.RelativeBlock> blueprint) {
	}

	private record CapturedStructure(
		List<GrandPalaceBlueprint.RelativeBlock> blocks,
		int width,
		int height,
		int depth,
		boolean hitLimit
	) {
	}

	private record BuildBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockPos anchor, Direction facing) {
	}

	private record RollbackData(ResourceKey<Level> dimensionKey, Component structureName, BlockPos anchor, Map<BlockPos, SnapshotBlock> snapshot) {
	}

	private record SnapshotBlock(BlockState state, CompoundTag blockEntityNbt) {
	}

	private static final class ColumnInfo {
		private int minY;
		private int maxY;

		private ColumnInfo(int y) {
			this.minY = y;
			this.maxY = y;
		}

		private void observe(int y) {
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
	}

	private record TerrainPalette(BlockState topState, BlockState fillState) {
	}

	private static final class PendingPreview {
		private static final int MAX_CONFLICT_MARKERS_PER_TICK = 12;
		private final ResourceKey<Level> dimensionKey;
		private final BlockPos origin;
		private final Direction facing;
		private final Component structureName;
		private final List<GrandPalaceBlueprint.RelativeBlock> blocks;
		private final BuildEffectMode effectMode;
		private final int totalBlocks;
		private final int minX;
		private final int maxX;
		private final int minY;
		private final int maxY;
		private final int minZ;
		private final int maxZ;
		private final int[] sampledBlockIndexes;
		private final int[] conflictSampleIndexes;
		private int sampleCursor;
		private int conflictCursor;
		private int tickCounter;

		private PendingPreview(ServerLevel level, ResourceKey<Level> dimensionKey, BlockPos origin, Direction facing, Component structureName, List<GrandPalaceBlueprint.RelativeBlock> blocks, BuildEffectMode effectMode, int sampleCap, GrandBuilderConfig config) {
			this.dimensionKey = dimensionKey;
			this.origin = origin;
			this.facing = facing;
			this.structureName = structureName;
			this.blocks = blocks;
			this.effectMode = effectMode;
			this.totalBlocks = blocks.size();

			int localMinX = 0;
			int localMaxX = 0;
			int localMinY = 0;
			int localMaxY = 0;
			int localMinZ = 0;
			int localMaxZ = 0;
			boolean first = true;

			for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
				BlockPos transformed = transform(BlockPos.ZERO, facing, block);
				if (first) {
					localMinX = transformed.getX();
					localMaxX = transformed.getX();
					localMinY = transformed.getY();
					localMaxY = transformed.getY();
					localMinZ = transformed.getZ();
					localMaxZ = transformed.getZ();
					first = false;
				} else {
					localMinX = Math.min(localMinX, transformed.getX());
					localMaxX = Math.max(localMaxX, transformed.getX());
					localMinY = Math.min(localMinY, transformed.getY());
					localMaxY = Math.max(localMaxY, transformed.getY());
					localMinZ = Math.min(localMinZ, transformed.getZ());
					localMaxZ = Math.max(localMaxZ, transformed.getZ());
				}
			}

			this.minX = localMinX;
			this.maxX = localMaxX;
			this.minY = localMinY;
			this.maxY = localMaxY;
			this.minZ = localMinZ;
			this.maxZ = localMaxZ;

			List<Integer> previewCandidates = new ArrayList<>(blocks.size());
			for (int i = 0; i < blocks.size(); i++) {
				if (!blocks.get(i).state().isAir()) {
					previewCandidates.add(i);
				}
			}
			if (previewCandidates.isEmpty()) {
				for (int i = 0; i < blocks.size(); i++) {
					previewCandidates.add(i);
				}
			}

			int effectiveCap = Math.max(1, sampleCap);
			int step = Math.max(1, (previewCandidates.size() + effectiveCap - 1) / effectiveCap);
			int sampleCount = Math.max(1, (previewCandidates.size() + step - 1) / step);
			this.sampledBlockIndexes = new int[sampleCount];
			int sampleIndex = 0;
			for (int i = 0; i < previewCandidates.size() && sampleIndex < sampleCount; i += step) {
				this.sampledBlockIndexes[sampleIndex++] = previewCandidates.get(i);
			}
			if (sampleIndex == 0 && !blocks.isEmpty()) {
				this.sampledBlockIndexes[0] = 0;
			}
			this.conflictSampleIndexes = sampleConflictIndexes(level, config);
		}

		private void tick(ServerLevel level, GrandBuilderConfig config) {
			if (blocks.isEmpty()) {
				return;
			}
			tickCounter++;
			if ((tickCounter % Math.max(1, config.previewParticleIntervalTicks)) == 0) {
				int drawCount = Math.min(Math.max(1, config.previewParticlesPerTick), sampledBlockIndexes.length);
				for (int i = 0; i < drawCount; i++) {
					int sampledIndex = sampledBlockIndexes[(sampleCursor + i) % sampledBlockIndexes.length];
					GrandPalaceBlueprint.RelativeBlock block = blocks.get(sampledIndex);
					BlockPos pos = transform(origin, facing, block);
					if (!level.isInWorldBounds(pos)) {
						continue;
					}
					BlockState state = rotateState(block.state(), facing);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
				}
				sampleCursor = (sampleCursor + drawCount) % sampledBlockIndexes.length;
				drawConflictMarkers(level, Math.min(MAX_CONFLICT_MARKERS_PER_TICK, Math.max(2, drawCount / 12)));
			}

			if ((tickCounter % Math.max(2, config.previewBoundsIntervalTicks)) == 0) {
				drawBounds(level);
			}
		}

		private int[] sampleConflictIndexes(ServerLevel level, GrandBuilderConfig config) {
			if (sampledBlockIndexes.length == 0) {
				return new int[0];
			}

			List<Integer> conflicts = new ArrayList<>();
			for (int sampledIndex : sampledBlockIndexes) {
				GrandPalaceBlueprint.RelativeBlock block = blocks.get(sampledIndex);
				BlockState targetState = rotateState(block.state(), facing);
				if (targetState.isAir()) {
					continue;
				}

				BlockPos pos = transform(origin, facing, block);
				if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
					continue;
				}

				BlockState existing = level.getBlockState(pos);
				if (!canReplace(level, pos, existing, targetState, config.replaceRule())) {
					conflicts.add(sampledIndex);
				}
			}

			int[] sampledConflicts = new int[conflicts.size()];
			for (int i = 0; i < conflicts.size(); i++) {
				sampledConflicts[i] = conflicts.get(i);
			}
			return sampledConflicts;
		}

		private void drawBounds(ServerLevel level) {
			int worldMinX = origin.getX() + minX;
			int worldMaxX = origin.getX() + maxX;
			int worldMinY = origin.getY() + minY;
			int worldMaxY = origin.getY() + maxY;
			int worldMinZ = origin.getZ() + minZ;
			int worldMaxZ = origin.getZ() + maxZ;

			int edgeStep = previewEdgeStep(width(), height(), depth());
			drawBoxEdge(level, worldMinX, worldMinY, worldMinZ, worldMaxX, worldMinY, worldMinZ, edgeStep, ParticleTypes.ENCHANT);
			drawBoxEdge(level, worldMinX, worldMinY, worldMaxZ, worldMaxX, worldMinY, worldMaxZ, edgeStep, ParticleTypes.ENCHANT);
			drawBoxEdge(level, worldMinX, worldMaxY, worldMinZ, worldMaxX, worldMaxY, worldMinZ, edgeStep, ParticleTypes.END_ROD);
			drawBoxEdge(level, worldMinX, worldMaxY, worldMaxZ, worldMaxX, worldMaxY, worldMaxZ, edgeStep, ParticleTypes.END_ROD);

			drawBoxEdge(level, worldMinX, worldMinY, worldMinZ, worldMinX, worldMinY, worldMaxZ, edgeStep, ParticleTypes.ENCHANT);
			drawBoxEdge(level, worldMaxX, worldMinY, worldMinZ, worldMaxX, worldMinY, worldMaxZ, edgeStep, ParticleTypes.ENCHANT);
			drawBoxEdge(level, worldMinX, worldMaxY, worldMinZ, worldMinX, worldMaxY, worldMaxZ, edgeStep, ParticleTypes.END_ROD);
			drawBoxEdge(level, worldMaxX, worldMaxY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ, edgeStep, ParticleTypes.END_ROD);

			drawBoxEdge(level, worldMinX, worldMinY, worldMinZ, worldMinX, worldMaxY, worldMinZ, edgeStep, ParticleTypes.CRIT);
			drawBoxEdge(level, worldMaxX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMinZ, edgeStep, ParticleTypes.CRIT);
			drawBoxEdge(level, worldMinX, worldMinY, worldMaxZ, worldMinX, worldMaxY, worldMaxZ, edgeStep, ParticleTypes.CRIT);
			drawBoxEdge(level, worldMaxX, worldMinY, worldMaxZ, worldMaxX, worldMaxY, worldMaxZ, edgeStep, ParticleTypes.CRIT);

			drawCornerBeacons(level, worldMinX, worldMaxX, worldMinY, worldMaxY, worldMinZ, worldMaxZ);
			drawFacingGuide(level, worldMinX, worldMaxX, worldMinY, worldMinZ, worldMaxZ);
		}

		private static int previewEdgeStep(int width, int height, int depth) {
			int longest = Math.max(width, Math.max(height, depth));
			return Math.max(1, (longest + 63) / 64);
		}

		private void drawBoxEdge(
			ServerLevel level,
			int startX,
			int startY,
			int startZ,
			int endX,
			int endY,
			int endZ,
			int step,
			ParticleOptions particle
		) {
			int dx = Integer.compare(endX, startX);
			int dy = Integer.compare(endY, startY);
			int dz = Integer.compare(endZ, startZ);
			int length = Math.max(Math.abs(endX - startX), Math.max(Math.abs(endY - startY), Math.abs(endZ - startZ)));
			for (int distance = 0; distance <= length; distance += step) {
				spawnMarker(level, particle, startX + dx * distance, startY + dy * distance, startZ + dz * distance);
			}
			spawnMarker(level, particle, endX, endY, endZ);
		}

		private void drawCornerBeacons(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
			int[] xs = {minX, maxX};
			int[] ys = {minY, maxY};
			int[] zs = {minZ, maxZ};
			for (int x : xs) {
				for (int y : ys) {
					for (int z : zs) {
						level.sendParticles(ParticleTypes.GLOW, x + 0.5, y + 0.5, z + 0.5, 2, 0.12, 0.12, 0.12, 0.0);
					}
				}
			}
		}

		private void drawFacingGuide(ServerLevel level, int worldMinX, int worldMaxX, int worldMinY, int worldMinZ, int worldMaxZ) {
			int centerX = (worldMinX + worldMaxX) / 2;
			int centerZ = (worldMinZ + worldMaxZ) / 2;
			int markerY = Math.min(worldMinY + Math.max(1, height() / 3), worldMinY + height() - 1);

			int startX = centerX;
			int startZ = centerZ;
			if (facing == Direction.NORTH) {
				startZ = worldMinZ;
			} else if (facing == Direction.SOUTH) {
				startZ = worldMaxZ;
			} else if (facing == Direction.EAST) {
				startX = worldMaxX;
			} else if (facing == Direction.WEST) {
				startX = worldMinX;
			}

			for (int i = 0; i < 4; i++) {
				spawnMarker(level, ParticleTypes.CRIT, startX + facing.getStepX() * i, markerY, startZ + facing.getStepZ() * i);
			}
		}

		private void drawConflictMarkers(ServerLevel level, int drawCount) {
			if (conflictSampleIndexes.length == 0 || drawCount <= 0) {
				return;
			}

			int count = Math.min(drawCount, conflictSampleIndexes.length);
			for (int i = 0; i < count; i++) {
				int sampledIndex = conflictSampleIndexes[(conflictCursor + i) % conflictSampleIndexes.length];
				GrandPalaceBlueprint.RelativeBlock block = blocks.get(sampledIndex);
				BlockPos pos = transform(origin, facing, block);
				if (!level.isInWorldBounds(pos)) {
					continue;
				}
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
			}
			conflictCursor = (conflictCursor + count) % conflictSampleIndexes.length;
		}

		private static void spawnMarker(ServerLevel level, ParticleOptions particle, int x, int y, int z) {
			level.sendParticles(particle, x + 0.5, y + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		}

		private int width() {
			return maxX - minX + 1;
		}

		private int height() {
			return maxY - minY + 1;
		}

		private int depth() {
			return maxZ - minZ + 1;
		}

		private int totalBlocks() {
			return totalBlocks;
		}

		private Component structureName() {
			return structureName;
		}

		private BuildEffectMode effectMode() {
			return effectMode;
		}
	}

	private static final class BuildJob {
		private final ResourceKey<Level> dimensionKey;
		private final BlockPos origin;
		private final Direction facing;
		private final UUID ownerId;
		private final Component structureName;
		private final List<GrandPalaceBlueprint.RelativeBlock> dryBlocks;
		private final List<GrandPalaceBlueprint.RelativeBlock> fluidBlocks;
		private final RollbackData rollbackData;
		private final BuildEffectMode effectMode;
		private final BuildBounds effectBounds;
		private final int totalBlocks;
		private int dryCursor;
		private int fluidCursor;
		private boolean paused;
		private boolean pausedByOffline;
		private int tickDelayCounter;
		private int effectTick;
		private int statusPulse;
		private int skippedBlocks;
		private boolean waitingForChunks;
		private boolean instantRevealPlacement;

		private BuildJob(
			ResourceKey<Level> dimensionKey,
			BlockPos origin,
			Direction facing,
			UUID ownerId,
			Component structureName,
			List<GrandPalaceBlueprint.RelativeBlock> blocks,
			RollbackData rollbackData,
			BuildEffectMode effectMode,
			BuildBounds effectBounds
		) {
			this.dimensionKey = dimensionKey;
			this.origin = origin;
			this.facing = facing;
			this.ownerId = ownerId;
			this.structureName = structureName;
			this.dryBlocks = new ArrayList<>();
			this.fluidBlocks = new ArrayList<>();
			for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
				if (block.state().getFluidState().isEmpty()) {
					this.dryBlocks.add(block);
				} else {
					this.fluidBlocks.add(block);
				}
			}
			this.rollbackData = rollbackData;
			this.effectMode = effectMode;
			this.effectBounds = effectBounds;
			this.totalBlocks = this.dryBlocks.size() + this.fluidBlocks.size();
		}

		private boolean tick(ServerLevel level, ServerPlayer owner, BuildSpeed speed, GrandBuilderConfig config) {
			if (paused || pausedByOffline) {
				waitingForChunks = false;
				return false;
			}
			if (effectMode == BuildEffectMode.UFO_INVASION) {
				return tickUfoReveal(level, owner, config);
			}
			tickBuildEffect(level);
			tickDelayCounter++;
			if (tickDelayCounter < Math.max(1, speed.tickDelay())) {
				waitingForChunks = false;
				return false;
			}
			tickDelayCounter = 0;

			int attemptsBudget = Math.min(config.maxBlocksPerTick, Math.max(1, speed.blocksPerCycle()));
			waitingForChunks = false;

			for (int attempts = 0; attempts < attemptsBudget; attempts++) {
				if (dryCursor < dryBlocks.size()) {
					PlacementResult result = placeBlock(level, dryBlocks.get(dryCursor), dryCursor + 1, config);
					if (result == PlacementResult.WAITING_FOR_CHUNK) {
						waitingForChunks = true;
						break;
					}
					dryCursor++;
					if (result == PlacementResult.SKIPPED) {
						skippedBlocks++;
					}
					continue;
				}

				if (fluidCursor < fluidBlocks.size()) {
					PlacementResult result = placeBlock(level, fluidBlocks.get(fluidCursor), dryBlocks.size() + fluidCursor + 1, config);
					if (result == PlacementResult.WAITING_FOR_CHUNK) {
						waitingForChunks = true;
						break;
					}
					fluidCursor++;
					if (result == PlacementResult.SKIPPED) {
						skippedBlocks++;
					}
					continue;
				}
				break;
			}
			return dryCursor >= dryBlocks.size() && fluidCursor >= fluidBlocks.size();
		}

		private boolean tickUfoReveal(ServerLevel level, ServerPlayer owner, GrandBuilderConfig config) {
			tickBuildEffect(level);
			if (effectTick < UFO_REVEAL_DELAY_TICKS) {
				waitingForChunks = false;
				return false;
			}

			if (!areTargetChunksReady(level, config)) {
				waitingForChunks = true;
				return false;
			}

			waitingForChunks = false;
			boolean finished = placeAllInstantly(level, config);
			if (finished) {
				spawnUfoRevealBurst(level);
				if (owner != null) {
					sendBuildEffect(owner, effectMode, BuildEffectPayload.PHASE_REVEAL, 28, 1.85f);
				}
			}
			return finished;
		}

		private boolean areTargetChunksReady(ServerLevel level, GrandBuilderConfig config) {
			if (!config.pauseWhenChunksMissing) {
				return true;
			}

			for (GrandPalaceBlueprint.RelativeBlock block : dryBlocks) {
				BlockPos targetPos = transform(origin, facing, block);
				if (level.isInWorldBounds(targetPos) && !level.isLoaded(targetPos)) {
					return false;
				}
			}
			for (GrandPalaceBlueprint.RelativeBlock block : fluidBlocks) {
				BlockPos targetPos = transform(origin, facing, block);
				if (level.isInWorldBounds(targetPos) && !level.isLoaded(targetPos)) {
					return false;
				}
			}
			return true;
		}

		private boolean placeAllInstantly(ServerLevel level, GrandBuilderConfig config) {
			instantRevealPlacement = true;
			try {
				while (dryCursor < dryBlocks.size()) {
					PlacementResult result = placeBlock(level, dryBlocks.get(dryCursor), dryCursor + 1, config);
					if (result == PlacementResult.WAITING_FOR_CHUNK) {
						waitingForChunks = true;
						return false;
					}
					dryCursor++;
					if (result == PlacementResult.SKIPPED) {
						skippedBlocks++;
					}
				}
				while (fluidCursor < fluidBlocks.size()) {
					PlacementResult result = placeBlock(level, fluidBlocks.get(fluidCursor), dryBlocks.size() + fluidCursor + 1, config);
					if (result == PlacementResult.WAITING_FOR_CHUNK) {
						waitingForChunks = true;
						return false;
					}
					fluidCursor++;
					if (result == PlacementResult.SKIPPED) {
						skippedBlocks++;
					}
				}
			} finally {
				instantRevealPlacement = false;
			}
			return true;
		}

		private void tickBuildEffect(ServerLevel level) {
			if (effectMode != BuildEffectMode.UFO_INVASION) {
				return;
			}

			effectTick++;
			double centerX = (effectBounds.minX() + effectBounds.maxX()) * 0.5 + 0.5;
			double centerZ = (effectBounds.minZ() + effectBounds.maxZ()) * 0.5 + 0.5;
			double buildWidth = Math.max(1.0, effectBounds.maxX() - effectBounds.minX() + 1.0);
			double buildDepth = Math.max(1.0, effectBounds.maxZ() - effectBounds.minZ() + 1.0);
			double radius = Math.max(4.0, Math.min(18.0, Math.max(buildWidth, buildDepth) * 0.62));
			double arrival = Math.max(0.0, 1.0 - Math.min(1.0, effectTick / (double) UFO_REVEAL_DELAY_TICKS));
			double shipX = centerX - facing.getStepX() * arrival * (radius + 16.0);
			double shipZ = centerZ - facing.getStepZ() * arrival * (radius + 16.0);
			double shipY = Math.min(level.getMaxY() - 2.0, effectBounds.maxY() + 8.0 + Math.sin(effectTick * 0.10) * 1.1);
			double beamBottom = effectBounds.minY() + 0.35;
			double spin = effectTick * 0.20;

			for (int i = 0; i < 24; i++) {
				double angle = spin + (Math.PI * 2.0 * i) / 24.0;
				double x = shipX + Math.cos(angle) * radius;
				double z = shipZ + Math.sin(angle) * radius;
				double y = shipY + Math.sin(angle * 3.0 + effectTick * 0.12) * 0.25;
				level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
			}

			for (int i = 0; i < 9; i++) {
				double t = i / 8.0;
				double y = shipY + (beamBottom - shipY) * t;
				double spread = 0.18 + t * Math.min(2.4, radius * 0.18);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, shipX, y, shipZ, 2, spread, 0.03, spread, 0.02);
				if ((effectTick + i) % 3 == 0) {
					level.sendParticles(ParticleTypes.WITCH, shipX, y, shipZ, 1, spread * 0.65, 0.02, spread * 0.65, 0.0);
				}
			}

			double beamRadius = Math.min(3.8, Math.max(1.4, radius * 0.22));
			if (arrival <= 0.18) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerX, effectBounds.minY() + 1.0, centerZ, 10, beamRadius, 0.35, beamRadius, 0.08);
				level.sendParticles(ParticleTypes.END_ROD, centerX, effectBounds.minY() + 1.2, centerZ, 8, beamRadius * 0.75, 0.45, beamRadius * 0.75, 0.05);
			}

			if (effectTick <= UFO_REVEAL_DELAY_TICKS && (effectTick % 5) == 0) {
				level.sendParticles(ParticleTypes.SONIC_BOOM, shipX, shipY, shipZ, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if ((effectTick % 16) == 1) {
				level.playSound(null, BlockPos.containing(shipX, shipY, shipZ), SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.45f, 0.65f);
				level.playSound(null, BlockPos.containing(shipX, shipY, shipZ), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.35f, 1.8f);
			}
		}

		private void spawnUfoRevealBurst(ServerLevel level) {
			double centerX = (effectBounds.minX() + effectBounds.maxX()) * 0.5 + 0.5;
			double centerY = Math.min(level.getMaxY() - 2.0, effectBounds.maxY() + 2.2);
			double centerZ = (effectBounds.minZ() + effectBounds.maxZ()) * 0.5 + 0.5;
			double width = Math.max(1.0, effectBounds.maxX() - effectBounds.minX() + 1.0);
			double depth = Math.max(1.0, effectBounds.maxZ() - effectBounds.minZ() + 1.0);
			double radius = Math.max(3.5, Math.min(20.0, Math.max(width, depth) * 0.56));
			BlockPos center = BlockPos.containing(centerX, centerY, centerZ);

			level.sendParticles(ParticleTypes.SONIC_BOOM, centerX, centerY, centerZ, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centerX, centerY, centerZ, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerX, centerY, centerZ, 180, radius, 3.0, radius, 0.28);
			level.sendParticles(ParticleTypes.END_ROD, centerX, centerY, centerZ, 140, radius * 0.65, 2.5, radius * 0.65, 0.18);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, centerX, centerY - 1.1, centerZ, 130, radius * 0.45, 1.7, radius * 0.45, 0.12);
			level.playSound(null, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.2f, 1.35f);
			level.playSound(null, center, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 1.0f, 1.85f);
			level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.9f, 0.55f);

			int sampleCount = Math.min(180, totalBlocks);
			if (sampleCount <= 0) {
				return;
			}
			int stride = Math.max(1, totalBlocks / sampleCount);
			for (int index = 0, spawned = 0; index < totalBlocks && spawned < sampleCount; index += stride, spawned++) {
				GrandPalaceBlueprint.RelativeBlock block = blockAt(index);
				BlockPos pos = transform(origin, facing, block);
				if (!level.isInWorldBounds(pos)) {
					continue;
				}
				double x = pos.getX() + 0.5;
				double y = pos.getY() + 0.65;
				double z = pos.getZ() + 0.5;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.22, 0.25, 0.22, 0.08);
				if ((spawned & 1) == 0) {
					level.sendParticles(ParticleTypes.END_ROD, x, y + 0.25, z, 1, 0.10, 0.16, 0.10, 0.02);
				}
			}
		}

		private GrandPalaceBlueprint.RelativeBlock blockAt(int index) {
			if (index < dryBlocks.size()) {
				return dryBlocks.get(index);
			}
			return fluidBlocks.get(index - dryBlocks.size());
		}

		private void finishEffects(ServerLevel level) {
			if (effectMode != BuildEffectMode.UFO_INVASION) {
				return;
			}

			double centerX = (effectBounds.minX() + effectBounds.maxX()) * 0.5 + 0.5;
			double centerY = Math.min(level.getMaxY() - 2.0, effectBounds.maxY() + 4.0);
			double centerZ = (effectBounds.minZ() + effectBounds.maxZ()) * 0.5 + 0.5;
			level.sendParticles(ParticleTypes.EXPLOSION, centerX, centerY, centerZ, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centerX, centerY, centerZ, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, centerX, centerY, centerZ, 80, 4.0, 2.0, 4.0, 0.10);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerX, centerY, centerZ, 90, 5.5, 2.5, 5.5, 0.18);
			level.playSound(null, BlockPos.containing(centerX, centerY, centerZ), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.9f, 1.25f);
			level.playSound(null, BlockPos.containing(centerX, centerY, centerZ), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
		}

		private PlacementResult placeBlock(ServerLevel level, GrandPalaceBlueprint.RelativeBlock block, int progressIndex, GrandBuilderConfig config) {
			BlockPos targetPos = transform(origin, facing, block);
			if (!level.isInWorldBounds(targetPos)) {
				return PlacementResult.SKIPPED;
			}
			if (config.pauseWhenChunksMissing && !level.isLoaded(targetPos)) {
				return PlacementResult.WAITING_FOR_CHUNK;
			}

			BlockState rotatedState = rotateState(block.state(), facing);
			boolean terrainOperation = isTerrainOperation(block.stage());
			if (terrainOperation && isTerrainClearOperation(block.stage()) && !level.canSeeSky(targetPos.above())) {
				return PlacementResult.SKIPPED;
			}

			BlockState existing = level.getBlockState(targetPos);
			if (!canReplace(level, targetPos, existing, rotatedState, config.replaceRule())) {
				return PlacementResult.SKIPPED;
			}

			level.setBlock(targetPos, rotatedState, 2);
			applyBlockEntityData(level, targetPos, rotatedState, block.blockEntityNbt());

			if (effectMode == BuildEffectMode.UFO_INVASION && !instantRevealPlacement) {
				spawnUfoPlacementEffect(level, targetPos, progressIndex);
			} else if ((progressIndex & 7) == 0) {
				level.sendParticles(ParticleTypes.END_ROD, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 1, 0.25, 0.25, 0.25, 0.01);
			}
			if (effectMode != BuildEffectMode.UFO_INVASION && (progressIndex % 24) == 0) {
				level.playSound(null, targetPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25f, 1.20f);
			}

			return PlacementResult.PLACED;
		}

		private void spawnUfoPlacementEffect(ServerLevel level, BlockPos targetPos, int progressIndex) {
			double x = targetPos.getX() + 0.5;
			double y = targetPos.getY() + 0.62;
			double z = targetPos.getZ() + 0.5;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.30, 0.25, 0.30, 0.04);
			if ((progressIndex & 1) == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 2, 0.20, 0.20, 0.20, 0.03);
			}
			if ((progressIndex % 6) == 0) {
				level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.15, z, 3, 0.22, 0.18, 0.22, 0.01);
			}
			if ((progressIndex % 14) == 0) {
				level.playSound(null, targetPos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 0.35f, 1.55f);
			}
			if ((progressIndex % 48) == 0) {
				level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.6, z, 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, targetPos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.55f, 1.8f);
			}
		}

		private static boolean isTerrainOperation(int stage) {
			return stage >= TERRAIN_FILL_STAGE_BASE;
		}

		private static boolean isTerrainClearOperation(int stage) {
			return stage >= TERRAIN_CLEAR_STAGE_BASE && stage < VEGETATION_CLEAR_STAGE_BASE;
		}

		private int remainingBlocks() {
			return Math.max(0, totalBlocks - placedBlocks());
		}

		private double progressPercent() {
			if (totalBlocks <= 0) {
				return 100.0;
			}
			return (placedBlocks() * 100.0) / totalBlocks;
		}

		private int placedBlocks() {
			return dryCursor + fluidCursor;
		}
	}

	private enum PlacementResult {
		PLACED,
		SKIPPED,
		WAITING_FOR_CHUNK
	}

	private static CompoundTag captureBlockEntityData(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
	}

	private static void applyBlockEntityData(ServerLevel level, BlockPos pos, BlockState state, CompoundTag template) {
		if (!state.hasBlockEntity()) {
			level.removeBlockEntity(pos);
			return;
		}
		if (template == null || template.isEmpty()) {
			level.removeBlockEntity(pos);
			return;
		}
		if (template.toString().length() > GrandBuilderConfig.get().maxBlockEntityNbtChars) {
			GrandBuilderMod.LOGGER.warn("Skipped oversized block entity NBT at {}", pos);
			level.removeBlockEntity(pos);
			return;
		}

		CompoundTag nbt = preparePlacedBlockEntityTag(template, pos);
		try {
			BlockEntity loaded = BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
			if (loaded != null) {
				loaded.setChanged();
				level.setBlockEntity(loaded);
			}
		} catch (Exception exception) {
			GrandBuilderMod.LOGGER.warn("Failed to place block entity at {}", pos, exception);
		}
	}

	private static CompoundTag preparePlacedBlockEntityTag(CompoundTag source, BlockPos pos) {
		CompoundTag nbt = source.copy();
		String id = nbt.getStringOr("id", "");
		if (id.isEmpty()) {
			id = nbt.getStringOr("Id", "");
			if (!id.isEmpty()) {
				nbt.putString("id", id);
			}
		}
		nbt.putInt("x", pos.getX());
		nbt.putInt("y", pos.getY());
		nbt.putInt("z", pos.getZ());
		return nbt;
	}
}
