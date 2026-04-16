package dev.grandbuilder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.build.BuildSpeed;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class GrandBuilderConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("grand_builder.json");

	private static volatile GrandBuilderConfig current = defaults();

	public String permissionMode = "ops_only";
	public int customPermissionLevel = 2;

	public int actionCooldownTicks = 3;
	public int maxConcurrentBuilds = 4;
	public int maxBlocksPerBuild = 50000000;
	public int maxBlocksPerTick = 512;
	public int maxBuildRadius = 2048;
	public int minSpawnDistance = 8;
	public int maxSpawnDistance = 2048;
	public int spawnDistancePadding = 5;

	public int maxPreviewBlocks = 50000000;
	public int previewSampleCap = 1400;
	public int previewParticlesPerTick = 180;
	public int previewParticleIntervalTicks = 2;
	public int previewBoundsIntervalTicks = 6;

	public int captureRadiusXZ = 8;
	public int captureDown = 4;
	public int captureUp = 12;
	public int maxCaptureBlocks = 1000000;

	public boolean allowExternalStructures = true;
	public int maxExternalStructureFiles = 512;

	public boolean requireSneakToConfirm = true;
	public boolean requireSneakForRollback = true;
	public int rollbackConfirmWindowTicks = 80;
	public int maxRollbackHistoryPerPlayer = 1;

	public boolean pauseWhenOwnerOffline = true;
	public boolean pauseWhenChunksMissing = true;
	public boolean terrainAdaptationEnabled = true;
	public int terrainAdaptMargin = 2;
	public int terrainSkirtRadius = 2;
	public int terrainAdaptMaxFillDepth = 20;
	public int terrainAdaptMaxCutHeight = 8;
	public boolean terrainClearNearbyTrees = true;
	public int terrainTreeClearRadius = 3;
	public int terrainTreeClearHeight = 24;
	public String terrainFoundationBlock = "minecraft:stone";

	public boolean requireStructureCoreInSurvival = true;
	public boolean consumeStructureCoreInSurvival = false;
	public boolean allowCreativeWithoutCore = true;

	public String replaceRule = "replaceable";
	public int maxBlockEntityNbtChars = 32768;

	public List<String> allowedDimensions = new ArrayList<>(List.of("minecraft:overworld"));
	public List<String> blockedDimensions = new ArrayList<>();

	public Map<String, SpeedProfile> speedProfiles = new LinkedHashMap<>();

	private transient EnumMap<BuildSpeed, SpeedProfile> resolvedSpeedProfiles;
	private transient PermissionMode resolvedPermissionMode;
	private transient ReplaceRule resolvedReplaceRule;
	private transient Set<String> normalizedAllowedDimensions;
	private transient Set<String> normalizedBlockedDimensions;

	public static synchronized void load() {
		GrandBuilderConfig next = null;
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				next = GSON.fromJson(reader, GrandBuilderConfig.class);
			} catch (Exception exception) {
				GrandBuilderMod.LOGGER.warn("Failed to read config {}, using defaults", CONFIG_PATH, exception);
			}
		}

		if (next == null) {
			next = defaults();
		}

		next.sanitize();
		current = next;
		writeCurrent();
	}

	public static synchronized void reload() {
		load();
	}

	public static GrandBuilderConfig get() {
		return current;
	}

	public static Path configPath() {
		return CONFIG_PATH;
	}

	public boolean hasPermission(ServerPlayer player) {
		return switch (resolvedPermissionMode) {
			case ANYONE -> true;
			case OPS_ONLY -> hasCommandLevel(player, 2);
			case LEVEL_2 -> hasCommandLevel(player, 2);
			case LEVEL_4 -> hasCommandLevel(player, 4);
			case CUSTOM -> hasCommandLevel(player, customPermissionLevel);
		};
	}

	public boolean isDimensionAllowed(ResourceKey<Level> dimensionKey) {
		String id = dimensionKey.identifier().toString();
		if (!normalizedAllowedDimensions.isEmpty() && !normalizedAllowedDimensions.contains(id)) {
			return false;
		}
		return !normalizedBlockedDimensions.contains(id);
	}

	public ReplaceRule replaceRule() {
		return resolvedReplaceRule;
	}

	public SpeedProfile speedProfile(BuildSpeed speed) {
		return resolvedSpeedProfiles.get(speed);
	}

	private void sanitize() {
		permissionMode = normalizeToken(permissionMode, "ops_only");
		resolvedPermissionMode = PermissionMode.fromConfig(permissionMode);
		customPermissionLevel = clamp(customPermissionLevel, 0, 4);

		actionCooldownTicks = clamp(actionCooldownTicks, 0, 200);
		maxConcurrentBuilds = clamp(maxConcurrentBuilds, 1, 64);
		maxBlocksPerBuild = clamp(maxBlocksPerBuild, 256, 200_000_000);
		maxBlocksPerTick = clamp(maxBlocksPerTick, 1, 4096);
		// Upgrade legacy defaults so newer top speeds are not silently capped by old tick budget values.
		if (maxBlocksPerTick <= 192 && (speedProfiles == null || !speedProfiles.containsKey("overdrive"))) {
			maxBlocksPerTick = 512;
		}
		maxBuildRadius = clamp(maxBuildRadius, 16, 10_000);
		minSpawnDistance = clamp(minSpawnDistance, 1, 1024);
		maxSpawnDistance = clamp(maxSpawnDistance, minSpawnDistance, 10_000);
		spawnDistancePadding = clamp(spawnDistancePadding, 0, 256);

		maxPreviewBlocks = clamp(maxPreviewBlocks, 128, maxBlocksPerBuild);
		previewSampleCap = clamp(previewSampleCap, 32, 5000);
		previewParticlesPerTick = clamp(previewParticlesPerTick, 4, 1024);
		previewParticleIntervalTicks = clamp(previewParticleIntervalTicks, 1, 60);
		previewBoundsIntervalTicks = clamp(previewBoundsIntervalTicks, 2, 120);

		captureRadiusXZ = clamp(captureRadiusXZ, 1, 64);
		captureDown = clamp(captureDown, 0, 64);
		captureUp = clamp(captureUp, 1, 128);
		if (maxCaptureBlocks <= 12000) {
			maxCaptureBlocks = 1000000;
		}
		maxCaptureBlocks = clamp(maxCaptureBlocks, 64, maxBlocksPerBuild);

		rollbackConfirmWindowTicks = clamp(rollbackConfirmWindowTicks, 20, 20 * 30);
		maxRollbackHistoryPerPlayer = clamp(maxRollbackHistoryPerPlayer, 1, 5);
		terrainAdaptMargin = clamp(terrainAdaptMargin, 0, 16);
		terrainSkirtRadius = clamp(terrainSkirtRadius, 0, 16);
		terrainAdaptMaxFillDepth = clamp(terrainAdaptMaxFillDepth, 1, 96);
		terrainAdaptMaxCutHeight = clamp(terrainAdaptMaxCutHeight, 0, 48);
		terrainTreeClearRadius = clamp(terrainTreeClearRadius, 0, 16);
		terrainTreeClearHeight = clamp(terrainTreeClearHeight, 0, 96);
		terrainFoundationBlock = normalizeBlockId(terrainFoundationBlock, "minecraft:stone");

		maxExternalStructureFiles = clamp(maxExternalStructureFiles, 1, 4096);
		maxBlockEntityNbtChars = clamp(maxBlockEntityNbtChars, 256, 1_000_000);

		replaceRule = normalizeToken(replaceRule, "replaceable");
		resolvedReplaceRule = ReplaceRule.fromConfig(replaceRule);

		normalizedAllowedDimensions = normalizeDimensionList(allowedDimensions);
		normalizedBlockedDimensions = normalizeDimensionList(blockedDimensions);

		if (speedProfiles == null) {
			speedProfiles = new LinkedHashMap<>();
		}

		resolvedSpeedProfiles = new EnumMap<>(BuildSpeed.class);
		for (BuildSpeed speed : BuildSpeed.values()) {
			SpeedProfile configured = speedProfiles.get(speed.configKey());
			if (configured == null) {
				configured = new SpeedProfile(speed.defaultBlocksPerCycle(), speed.defaultTickDelay());
			}

			configured.blocksPerCycle = clamp(configured.blocksPerCycle, 1, 2048);
			configured.tickDelay = clamp(configured.tickDelay, 1, 40);
			resolvedSpeedProfiles.put(speed, configured);
			speedProfiles.put(speed.configKey(), configured);
		}
	}

	private static Set<String> normalizeDimensionList(List<String> list) {
		if (list == null || list.isEmpty()) {
			return Set.of();
		}
		return list.stream()
			.map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
			.filter(value -> !value.isEmpty())
			.collect(Collectors.toSet());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean hasCommandLevel(ServerPlayer player, int level) {
		if (level <= 0) {
			return true;
		}
		return player.permissions().hasPermission(permissionForLevel(level));
	}

	private static Permission permissionForLevel(int level) {
		return switch (level) {
			case 1 -> Permissions.COMMANDS_MODERATOR;
			case 2 -> Permissions.COMMANDS_GAMEMASTER;
			case 3 -> Permissions.COMMANDS_ADMIN;
			default -> Permissions.COMMANDS_OWNER;
		};
	}

	private static String normalizeToken(String input, String fallback) {
		if (input == null || input.isBlank()) {
			return fallback;
		}
		return input.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeBlockId(String input, String fallback) {
		String normalized = normalizeToken(input, fallback);
		Identifier parsed = Identifier.tryParse(normalized);
		if (parsed == null) {
			return fallback;
		}
		return parsed.toString();
	}

	private static synchronized void writeCurrent() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(current, writer);
			}
		} catch (IOException exception) {
			GrandBuilderMod.LOGGER.warn("Failed to write config {}", CONFIG_PATH, exception);
		}
	}

	private static GrandBuilderConfig defaults() {
		GrandBuilderConfig config = new GrandBuilderConfig();
		for (BuildSpeed speed : BuildSpeed.values()) {
			config.speedProfiles.put(
				speed.configKey(),
				new SpeedProfile(speed.defaultBlocksPerCycle(), speed.defaultTickDelay())
			);
		}
		config.sanitize();
		return config;
	}

	public enum PermissionMode {
		ANYONE,
		OPS_ONLY,
		LEVEL_2,
		LEVEL_4,
		CUSTOM;

		private static PermissionMode fromConfig(String token) {
			return switch (token) {
				case "anyone" -> ANYONE;
				case "level_2", "permission_2" -> LEVEL_2;
				case "level_4", "permission_4" -> LEVEL_4;
				case "custom" -> CUSTOM;
				default -> OPS_ONLY;
			};
		}
	}

	public enum ReplaceRule {
		AIR_ONLY,
		REPLACEABLE,
		ALL;

		private static ReplaceRule fromConfig(String token) {
			return switch (token) {
				case "air_only" -> AIR_ONLY;
				case "all", "replace_all" -> ALL;
				default -> REPLACEABLE;
			};
		}
	}

	public static final class SpeedProfile {
		public int blocksPerCycle;
		public int tickDelay;

		public SpeedProfile() {
			this(1, 1);
		}

		public SpeedProfile(int blocksPerCycle, int tickDelay) {
			this.blocksPerCycle = blocksPerCycle;
			this.tickDelay = tickDelay;
		}
	}
}
