package dev.grandbuilder.build;

import dev.grandbuilder.config.GrandBuilderConfig;
import dev.grandbuilder.network.StructureListPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StructureLibrary {
	private static final Logger LOGGER = LoggerFactory.getLogger("grand_builder/structures");
	private static final Path PRIMARY_STRUCTURES_DIR = FabricLoader.getInstance().getGameDir().resolve("grand_builder").resolve("structures");
	private static final Path SHARED_STRUCTURES_DIR = detectSharedStructuresDir();
	private static final int MULTI_NBT_STRIDE = 48;
	private static final long EXTERNAL_CACHE_TTL_MILLIS = 5000L;

	private static final Pattern OFFSET_XYZ_PATTERN = Pattern.compile("(?i)^(.*?)[-_]x(-?\\d+)[-_]y(-?\\d+)[-_]z(-?\\d+)$");
	private static final Pattern OFFSET_CSV_PATTERN = Pattern.compile("(?i)^(.*?)[-_](-?\\d+),(-?\\d+),(-?\\d+)$");
	private static final Pattern OFFSET_GRID_PATTERN = Pattern.compile("(?i)^(.*?)[-_](\\d+)(i{1,6})([a-z])$");
	private static final List<String> EXTERNAL_DEFAULT_ORDER = List.of(
		"house_6",
		"victorianheightsmanor",
		"wooden_farm"
	);
	private static final Map<String, String> EXTERNAL_DEFAULT_NAMES = Map.of(
		"house_6", "House No. 6",
		"victorianheightsmanor", "Victorian Heights Manor",
		"wooden_farm", "Wooden Farm"
	);

	private static final Object EXTERNAL_CACHE_LOCK = new Object();
	private static volatile long externalCacheExpiresAt;
	private static volatile Map<String, ExternalStructure> externalCache = Map.of();

	private StructureLibrary() {
	}

	public static void ensureStructuresDirectory() {
		for (Path directory : structureDirectories()) {
			try {
				Files.createDirectories(directory);
			} catch (IOException exception) {
				LOGGER.warn("Unable to create structures directory: {}", directory, exception);
			}
		}
	}

	public static Path structuresDirectory() {
		ensureStructuresDirectory();
		return PRIMARY_STRUCTURES_DIR;
	}

	public static void clearExternalCache() {
		synchronized (EXTERNAL_CACHE_LOCK) {
			externalCacheExpiresAt = 0L;
			externalCache = Map.of();
		}
	}

	public static List<SelectionEntry> listBuiltinSelections() {
		List<SelectionEntry> selections = new ArrayList<>();
		for (BuildStructure structure : BuildStructure.values()) {
			selections.add(new SelectionEntry(structure.selectionKey(), Component.translatable(structure.translationKey())));
		}
		return selections;
	}

	public static List<SelectionEntry> listPreferredSelections() {
		ensureStructuresDirectory();

		List<ExternalStructure> externalStructures = sortedExternalStructures(externalStructuresCached().values());
		if (externalStructures.isEmpty()) {
			return listBuiltinSelections();
		}

		List<SelectionEntry> selections = new ArrayList<>();
		for (ExternalStructure structure : externalStructures) {
			selections.add(new SelectionEntry(structure.key(), Component.literal(structure.displayName())));
		}
		selections.add(new SelectionEntry(
			BuildStructure.CUSTOM.selectionKey(),
			Component.translatable(BuildStructure.CUSTOM.translationKey())
		));
		return selections;
	}

	public static SelectionEntry defaultSelectionEntry() {
		List<SelectionEntry> entries = listPreferredSelections();
		if (!entries.isEmpty()) {
			return entries.get(0);
		}
		return new SelectionEntry(
			BuildStructure.CUSTOM.selectionKey(),
			Component.translatable(BuildStructure.CUSTOM.translationKey())
		);
	}

	public static List<SelectionEntry> listSelections() {
		return listPreferredSelections();
	}

	public static List<StructureListPayload.Entry> listNetworkSelections() {
		ensureStructuresDirectory();
		List<StructureListPayload.Entry> selections = new ArrayList<>();

		List<ExternalStructure> externalStructures = sortedExternalStructures(externalStructuresCached().values());
		if (!externalStructures.isEmpty()) {
			for (ExternalStructure structure : externalStructures) {
				selections.add(new StructureListPayload.Entry(
					structure.key(),
					structure.displayName(),
					"",
					true
				));
			}
			selections.add(new StructureListPayload.Entry(
				BuildStructure.CUSTOM.selectionKey(),
				"",
				BuildStructure.CUSTOM.translationKey(),
				false
			));
			return selections;
		}

		for (BuildStructure structure : BuildStructure.values()) {
			selections.add(new StructureListPayload.Entry(
				structure.selectionKey(),
				"",
				structure.translationKey(),
				false
			));
		}

		return selections;
	}

	public static ResolvedStructure resolveSelection(String key) {
		BuildStructure builtIn = BuildStructure.bySelectionKey(key);
		if (builtIn != null) {
			return new ResolvedStructure(
				builtIn.selectionKey(),
				Component.translatable(builtIn.translationKey()),
				builtIn.spawnDistance(),
				builtIn.isCustom(),
				builtIn.createBlueprint()
			);
		}

		ExternalStructure external = externalStructuresCached().get(key);
		if (external != null) {
			return new ResolvedStructure(
				external.key(),
				Component.literal(external.displayName()),
				external.spawnDistance(),
				false,
				external.blueprint()
			);
		}

		List<ExternalStructure> externalStructures = sortedExternalStructures(externalStructuresCached().values());
		if (!externalStructures.isEmpty()) {
			ExternalStructure fallbackExternal = externalStructures.get(0);
			return new ResolvedStructure(
				fallbackExternal.key(),
				Component.literal(fallbackExternal.displayName()),
				fallbackExternal.spawnDistance(),
				false,
				fallbackExternal.blueprint()
			);
		}

		BuildStructure fallback = BuildStructure.CUSTOM;
		return new ResolvedStructure(
			fallback.selectionKey(),
			Component.translatable(fallback.translationKey()),
			fallback.spawnDistance(),
			fallback.isCustom(),
			fallback.createBlueprint()
		);
	}

	private static Map<String, ExternalStructure> externalStructuresCached() {
		long now = System.currentTimeMillis();
		Map<String, ExternalStructure> snapshot = externalCache;
		if (now < externalCacheExpiresAt && !snapshot.isEmpty()) {
			return snapshot;
		}

		synchronized (EXTERNAL_CACHE_LOCK) {
			if (now < externalCacheExpiresAt && !externalCache.isEmpty()) {
				return externalCache;
			}

			Map<String, ExternalStructure> loaded = loadExternalStructuresUncached();
			externalCache = loaded;
			externalCacheExpiresAt = now + EXTERNAL_CACHE_TTL_MILLIS;
			return loaded;
		}
	}

	private static Map<String, ExternalStructure> loadExternalStructuresUncached() {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!config.allowExternalStructures) {
			return Map.of();
		}

		Map<String, ExternalStructure> result = new HashMap<>();
		ensureStructuresDirectory();

		Map<String, List<PieceWithOffset>> groupedPieces = new HashMap<>();
		List<ParsedPiece> singlePieces = new ArrayList<>();
		int[] filesSeen = {0};

		for (Path directory : structureDirectories()) {
			try (var stream = Files.list(directory)) {
				stream.forEach(path -> {
					if (filesSeen[0] >= config.maxExternalStructureFiles) {
						return;
					}

					if (Files.isDirectory(path)) {
						addExternal(result, loadDirectoryPack(path));
						return;
					}

					if (!Files.isRegularFile(path) || !isSupportedStructureFile(path)) {
						return;
					}
					filesSeen[0]++;

					ParsedPiece piece = parsePiece(path);
					if (piece == null || piece.blocks().isEmpty()) {
						LOGGER.warn("Skipped structure file {} because it could not be parsed or contained no blocks", path);
						return;
					}

					OffsetToken offset = parseOffsetToken(piece.baseName());
					if (offset == null) {
						singlePieces.add(piece);
						return;
					}

					groupedPieces.computeIfAbsent(offset.groupName(), ignored -> new ArrayList<>())
						.add(new PieceWithOffset(piece, offset));
				});
			} catch (IOException exception) {
				LOGGER.warn("Unable to read structures directory: {}", directory, exception);
			}
		}

		for (Map.Entry<String, List<PieceWithOffset>> entry : groupedPieces.entrySet()) {
			List<PieceWithOffset> pieces = entry.getValue();
			if (pieces.size() < 2) {
				singlePieces.add(pieces.get(0).piece());
				continue;
			}

			String sourceName = entry.getKey() + " (multi)";
			addExternal(result, buildExternalStructure(sourceName, combineOffsetPieces(pieces)));
		}

		for (ParsedPiece piece : singlePieces) {
			addExternal(result, buildExternalStructure(piece.baseName(), piece.blocks()));
		}

		if (filesSeen[0] >= config.maxExternalStructureFiles) {
			LOGGER.warn("External structure scan limit reached ({} files). Increase maxExternalStructureFiles in config if needed.", config.maxExternalStructureFiles);
		}

		return result;
	}

	private static List<Path> structureDirectories() {
		List<Path> directories = new ArrayList<>(2);
		directories.add(PRIMARY_STRUCTURES_DIR);
		if (SHARED_STRUCTURES_DIR != null && !SHARED_STRUCTURES_DIR.equals(PRIMARY_STRUCTURES_DIR)) {
			directories.add(SHARED_STRUCTURES_DIR);
		}
		return directories;
	}

	private static Path detectSharedStructuresDir() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path parent = gameDir.getParent();
		if (parent == null || parent.getFileName() == null) {
			return null;
		}
		if (!"versions".equalsIgnoreCase(parent.getFileName().toString())) {
			return null;
		}
		Path minecraftDir = parent.getParent();
		return minecraftDir == null ? null : minecraftDir.resolve("grand_builder").resolve("structures");
	}

	private static ExternalStructure loadDirectoryPack(Path directory) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		List<PieceWithOffset> offsetPieces = new ArrayList<>();
		List<RawBlock> plainBlocks = new ArrayList<>();
		int[] scanned = {0};

		try (var stream = Files.walk(directory)) {
			stream
				.filter(Files::isRegularFile)
				.filter(StructureLibrary::isSupportedStructureFile)
				.forEach(path -> {
					if (scanned[0] >= config.maxExternalStructureFiles) {
						return;
					}

					scanned[0]++;
					ParsedPiece piece = parsePiece(path);
					if (piece == null || piece.blocks().isEmpty()) {
						return;
					}

					OffsetToken offset = parseOffsetToken(piece.baseName());
					if (offset != null) {
						offsetPieces.add(new PieceWithOffset(piece, offset));
						return;
					}

					plainBlocks.addAll(piece.blocks());
				});
		} catch (IOException exception) {
			LOGGER.warn("Unable to read structure pack directory: {}", directory, exception);
			return null;
		}

		List<RawBlock> combined = new ArrayList<>(plainBlocks);
		combined.addAll(combineOffsetPieces(offsetPieces));
		if (combined.isEmpty()) {
			return null;
		}

		String displayName = directory.getFileName() != null
			? directory.getFileName().toString()
			: directory.toString();
		return buildExternalStructure(displayName, dedupeByPosition(combined));
	}

	private static List<RawBlock> combineOffsetPieces(List<PieceWithOffset> pieces) {
		List<RawBlock> combined = new ArrayList<>();
		for (PieceWithOffset piece : pieces) {
			int offsetX = piece.offset().gridUnits() ? piece.offset().x() * MULTI_NBT_STRIDE : piece.offset().x();
			int offsetY = piece.offset().gridUnits() ? piece.offset().y() * MULTI_NBT_STRIDE : piece.offset().y();
			int offsetZ = piece.offset().gridUnits() ? piece.offset().z() * MULTI_NBT_STRIDE : piece.offset().z();
			for (RawBlock block : piece.piece().blocks()) {
				combined.add(new RawBlock(
					block.x() + offsetX,
					block.y() + offsetY,
					block.z() + offsetZ,
					block.state(),
					copyNbt(block.blockEntityNbt())
				));
			}
		}
		return dedupeByPosition(combined);
	}

	private static List<RawBlock> dedupeByPosition(List<RawBlock> blocks) {
		Map<Vec3i, RawBlock> byPos = new HashMap<>();
		for (RawBlock block : blocks) {
			Vec3i key = new Vec3i(block.x(), block.y(), block.z());
			RawBlock existing = byPos.get(key);
			if (existing == null) {
				byPos.put(key, block);
				continue;
			}
			if (existing.state().isAir() && !block.state().isAir()) {
				byPos.put(key, block);
				continue;
			}
			if (!existing.state().isAir() && block.state().isAir()) {
				continue;
			}
			byPos.put(key, block);
		}
		return new ArrayList<>(byPos.values());
	}

	private static void addExternal(Map<String, ExternalStructure> result, ExternalStructure structure) {
		if (structure == null) {
			return;
		}

		String baseKey = structure.key();
		String key = baseKey;
		int suffix = 2;
		while (result.containsKey(key)) {
			key = baseKey + "_" + suffix;
			suffix++;
		}

		if (key.equals(baseKey)) {
			result.put(key, structure);
			return;
		}

		result.put(key, new ExternalStructure(
			key,
			structure.displayName(),
			structure.spawnDistance(),
			structure.blueprint(),
			structure.sourceToken()
		));
	}

	private static ParsedPiece parsePiece(Path path) {
		try {
			String filename = path.getFileName().toString();
			String baseName = baseName(filename);
			String extension = extensionOf(filename);
			CompoundTag root = unwrapStructureRoot(readNbtAuto(path), extension);
			ParsedPiece parsed = switch (extension) {
				case ".nbt" -> parseVanillaStructure(root, baseName);
				case ".schem" -> parseSpongeStructure(root, baseName);
				case ".schematic" -> parseSchematicStructure(root, baseName);
				case ".litematic" -> parseLitematicStructure(root, baseName);
				default -> null;
			};
			return parsed;
		} catch (Exception exception) {
			LOGGER.warn("Unable to load structure from {}", path, exception);
			return null;
		}
	}

	private static CompoundTag unwrapStructureRoot(CompoundTag root, String extension) {
		if ((".schem".equals(extension) || ".schematic".equals(extension)) && root.contains("Schematic")) {
			CompoundTag nested = root.getCompoundOrEmpty("Schematic");
			if (!nested.isEmpty()) {
				return nested;
			}
		}

		if (".litematic".equals(extension) && root.contains("Litematic")) {
			CompoundTag nested = root.getCompoundOrEmpty("Litematic");
			if (!nested.isEmpty()) {
				return nested;
			}
		}

		return root;
	}

	private static CompoundTag readNbtAuto(Path path) throws IOException {
		try {
			return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
		} catch (IOException compressedException) {
			return NbtIo.read(path);
		}
	}

	private static ParsedPiece parseVanillaStructure(CompoundTag root, String baseName) {
		ListTag paletteTag = root.getListOrEmpty("palette");
		ListTag blocksTag = root.getListOrEmpty("blocks");
		if (paletteTag.isEmpty() || blocksTag.isEmpty()) {
			return null;
		}

		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(readBlockState(paletteTag.getCompoundOrEmpty(i)));
		}

		List<RawBlock> blocks = new ArrayList<>();
		for (int i = 0; i < blocksTag.size(); i++) {
			CompoundTag blockTag = blocksTag.getCompoundOrEmpty(i);
			int paletteIndex = blockTag.getIntOr("state", -1);
			if (paletteIndex < 0 || paletteIndex >= palette.size()) {
				continue;
			}

			BlockState state = palette.get(paletteIndex);

			ListTag posTag = blockTag.getListOrEmpty("pos");
			CompoundTag blockEntityNbt = blockTag.getCompoundOrEmpty("nbt");
			blocks.add(new RawBlock(
				posTag.getIntOr(0, 0),
				posTag.getIntOr(1, 0),
				posTag.getIntOr(2, 0),
				state,
				blockEntityNbt.isEmpty() ? null : normalizeBlockEntityTag(blockEntityNbt)
			));
		}

		return new ParsedPiece(baseName, blocks);
	}

	private static ParsedPiece parseSpongeStructure(CompoundTag root, String baseName) {
		int sizeX = root.getIntOr("Width", 0);
		int sizeY = root.getIntOr("Height", 0);
		int sizeZ = root.getIntOr("Length", 0);
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			return null;
		}

		CompoundTag blocksSection = root.getCompoundOrEmpty("Blocks");
		CompoundTag paletteTag = root.getCompoundOrEmpty("Palette");
		byte[] blockData = getByteArrayOrEmpty(root, "BlockData");
		if ((paletteTag.isEmpty() || blockData.length == 0) && !blocksSection.isEmpty()) {
			paletteTag = blocksSection.getCompoundOrEmpty("Palette");
			blockData = getByteArrayOrEmpty(blocksSection, "BlockData");
			if (blockData.length == 0) {
				blockData = getByteArrayOrEmpty(blocksSection, "Data");
			}
		}
		if (paletteTag.isEmpty() || blockData.length == 0) {
			return null;
		}

		Map<Integer, BlockState> palette = new HashMap<>();
		for (String key : paletteTag.keySet()) {
			int id = paletteTag.getIntOr(key, -1);
			if (id < 0) {
				continue;
			}
			palette.put(id, readBlockStateFromString(key));
		}

		int total = sizeX * sizeY * sizeZ;
		int[] indexes = decodeVarInts(blockData, total);
		ListTag blockEntityList = root.getListOrEmpty("BlockEntities");
		if (blockEntityList.isEmpty() && !blocksSection.isEmpty()) {
			blockEntityList = blocksSection.getListOrEmpty("BlockEntities");
		}
		Map<Vec3i, CompoundTag> blockEntities = readBlockEntityMap(blockEntityList, 0, 0, 0, false);
		List<RawBlock> blocks = new ArrayList<>();

		for (int index = 0; index < Math.min(total, indexes.length); index++) {
			BlockState state = palette.getOrDefault(indexes[index], Blocks.AIR.defaultBlockState());

			int x = index % sizeX;
			int z = (index / sizeX) % sizeZ;
			int y = index / (sizeX * sizeZ);
			blocks.add(new RawBlock(
				x,
				y,
				z,
				state,
				copyNbt(blockEntities.get(new Vec3i(x, y, z)))
			));
		}

		return new ParsedPiece(baseName, blocks);
	}

	private static ParsedPiece parseSchematicStructure(CompoundTag root, String baseName) {
		CompoundTag spongePalette = root.getCompoundOrEmpty("Palette");
		byte[] spongeData = getByteArrayOrEmpty(root, "BlockData");
		CompoundTag spongeBlocks = root.getCompoundOrEmpty("Blocks");
		boolean nestedSpongeFormat = !spongeBlocks.isEmpty()
			&& !spongeBlocks.getCompoundOrEmpty("Palette").isEmpty()
			&& (
				getByteArrayOrEmpty(spongeBlocks, "BlockData").length > 0
				|| getByteArrayOrEmpty(spongeBlocks, "Data").length > 0
			);
		if ((!spongePalette.isEmpty() && spongeData.length > 0) || nestedSpongeFormat) {
			return parseSpongeStructure(root, baseName);
		}

		int sizeX = root.getIntOr("Width", 0);
		int sizeY = root.getIntOr("Height", 0);
		int sizeZ = root.getIntOr("Length", 0);
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			return null;
		}

		byte[] blocksArray = getByteArrayOrEmpty(root, "Blocks");
		if (blocksArray.length == 0) {
			return null;
		}

		byte[] addBlocks = getByteArrayOrEmpty(root, "AddBlocks");
		CompoundTag mapping = root.getCompoundOrEmpty("SchematicaMapping");
		if (mapping.isEmpty()) {
			mapping = root.getCompoundOrEmpty("BlockIDs");
		}
		if (mapping.isEmpty()) {
			return null;
		}

		Map<Integer, BlockState> idToState = new HashMap<>();
		for (String key : mapping.keySet()) {
			int legacyId = mapping.getIntOr(key, -1);
			if (legacyId < 0) {
				continue;
			}
			idToState.put(legacyId, readBlockStateFromString(normalizeBlockName(key)));
		}
		if (idToState.isEmpty()) {
			return null;
		}

		int total = Math.min(blocksArray.length, sizeX * sizeY * sizeZ);
		Map<Vec3i, CompoundTag> blockEntities = readBlockEntityMap(root.getListOrEmpty("TileEntities"), 0, 0, 0, false);
		List<RawBlock> blocks = new ArrayList<>();

		for (int index = 0; index < total; index++) {
			int id = blocksArray[index] & 0xFF;
			if (addBlocks.length > (index >> 1)) {
				int nibble = ((index & 1) == 0)
					? addBlocks[index >> 1] & 0x0F
					: (addBlocks[index >> 1] >>> 4) & 0x0F;
				id |= nibble << 8;
			}

			BlockState state = idToState.getOrDefault(id, Blocks.AIR.defaultBlockState());

			int x = index % sizeX;
			int z = (index / sizeX) % sizeZ;
			int y = index / (sizeX * sizeZ);
			blocks.add(new RawBlock(
				x,
				y,
				z,
				state,
				copyNbt(blockEntities.get(new Vec3i(x, y, z)))
			));
		}

		return new ParsedPiece(baseName, blocks);
	}

	private static ParsedPiece parseLitematicStructure(CompoundTag root, String baseName) {
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		if (regions.isEmpty()) {
			return null;
		}

		List<RawBlock> blocks = new ArrayList<>();

		for (String regionName : regions.keySet()) {
			CompoundTag region = regions.getCompoundOrEmpty(regionName);
			Vec3i size = readVec3(region, "Size");
			Vec3i position = readVec3(region, "Position");
			ListTag paletteTag = region.getListOrEmpty("BlockStatePalette");
			long[] packedStates = getLongArrayOrEmpty(region, "BlockStates");

			if (size == null || position == null || paletteTag.isEmpty() || packedStates.length == 0) {
				continue;
			}

			int rawSizeX = size.x();
			int rawSizeY = size.y();
			int rawSizeZ = size.z();
			int sizeX = Math.max(1, Math.abs(rawSizeX));
			int sizeY = Math.max(1, Math.abs(rawSizeY));
			int sizeZ = Math.max(1, Math.abs(rawSizeZ));

			int posX = position.x();
			int posY = position.y();
			int posZ = position.z();
			Map<Vec3i, CompoundTag> blockEntities = readBlockEntityMap(region.getListOrEmpty("TileEntities"), posX, posY, posZ, true);

			List<BlockState> palette = new ArrayList<>(paletteTag.size());
			for (int i = 0; i < paletteTag.size(); i++) {
				palette.add(readBlockState(paletteTag.getCompoundOrEmpty(i)));
			}
			if (palette.isEmpty()) {
				continue;
			}

			int bits = bitsRequired(palette.size());
			int total = sizeX * sizeY * sizeZ;
			for (int index = 0; index < total; index++) {
				int paletteIndex = readPackedIndex(packedStates, bits, index);
				if (paletteIndex < 0 || paletteIndex >= palette.size()) {
					continue;
				}

				BlockState state = palette.get(paletteIndex);

				int x = index % sizeX;
				int z = (index / sizeX) % sizeZ;
				int y = index / (sizeX * sizeZ);

				if (rawSizeX < 0) {
					x = sizeX - 1 - x;
				}
				if (rawSizeY < 0) {
					y = sizeY - 1 - y;
				}
				if (rawSizeZ < 0) {
					z = sizeZ - 1 - z;
				}

				int worldX = posX + x;
				int worldY = posY + y;
				int worldZ = posZ + z;
				CompoundTag blockEntityNbt = copyNbt(blockEntities.get(new Vec3i(worldX, worldY, worldZ)));
				if (blockEntityNbt == null) {
					blockEntityNbt = copyNbt(blockEntities.get(new Vec3i(x, y, z)));
				}

				blocks.add(new RawBlock(worldX, worldY, worldZ, state, blockEntityNbt));
			}
		}

		return blocks.isEmpty() ? null : new ParsedPiece(baseName, blocks);
	}

	private static ExternalStructure buildExternalStructure(String sourceName, List<RawBlock> rawBlocks) {
		if (rawBlocks.isEmpty()) {
			return null;
		}
		GrandBuilderConfig config = GrandBuilderConfig.get();
		String sourceToken = normalizeSourceToken(sourceName);
		String displayName = prettifyExternalStructureName(sourceName);

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		boolean hasSolidBlocks = false;

		for (RawBlock block : rawBlocks) {
			if (block.state().isAir()) {
				continue;
			}
			hasSolidBlocks = true;
			minX = Math.min(minX, block.x());
			minY = Math.min(minY, block.y());
			minZ = Math.min(minZ, block.z());
			maxX = Math.max(maxX, block.x());
			maxY = Math.max(maxY, block.y());
			maxZ = Math.max(maxZ, block.z());
		}
		if (!hasSolidBlocks) {
			return null;
		}

		int sizeX = Math.max(1, maxX - minX + 1);
		int sizeY = Math.max(1, maxY - minY + 1);
		int sizeZ = Math.max(1, maxZ - minZ + 1);
		int centerX = sizeX / 2;
		int centerZ = sizeZ / 2;
		int spawnDistance = Math.max(10, Math.max(sizeX, sizeZ) / 2 + config.spawnDistancePadding);
		spawnDistance = Math.max(config.minSpawnDistance, Math.min(config.maxSpawnDistance, spawnDistance));

		List<GrandPalaceBlueprint.RelativeBlock> blueprint = new ArrayList<>(rawBlocks.size());
		for (RawBlock block : rawBlocks) {
			if (block.x() < minX || block.x() > maxX || block.y() < minY || block.y() > maxY || block.z() < minZ || block.z() > maxZ) {
				continue;
			}
			int x = (block.x() - minX) - centerX;
			int y = block.y() - minY;
			int z = (block.z() - minZ) - centerZ;
			blueprint.add(new GrandPalaceBlueprint.RelativeBlock(
				x,
				y,
				z,
				block.state(),
				stageFor(x, y, z),
				copyNbt(block.blockEntityNbt())
			));
		}

		blueprint.sort(Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage));
		String key = "file:" + sourceToken;

		return new ExternalStructure(
			key,
			displayName,
			Math.max(spawnDistance, Math.min(config.maxSpawnDistance, Math.max(config.minSpawnDistance, sizeY / 2 + 8))),
			blueprint,
			sourceToken
		);
	}

	private static List<ExternalStructure> sortedExternalStructures(Iterable<ExternalStructure> source) {
		List<ExternalStructure> sorted = new ArrayList<>();
		for (ExternalStructure structure : source) {
			sorted.add(structure);
		}
		sorted.sort(
			Comparator
				.comparingInt(StructureLibrary::externalOrderIndex)
				.thenComparing(ExternalStructure::displayName, String::compareToIgnoreCase)
		);
		return sorted;
	}

	private static int externalOrderIndex(ExternalStructure structure) {
		int index = EXTERNAL_DEFAULT_ORDER.indexOf(structure.sourceToken());
		return index >= 0 ? index : Integer.MAX_VALUE;
	}

	private static String prettifyExternalStructureName(String sourceName) {
		String sourceToken = normalizeSourceToken(sourceName);
		String preferred = EXTERNAL_DEFAULT_NAMES.get(sourceToken);
		if (preferred != null) {
			return preferred;
		}

		String cleaned = stripDecorativeSuffixes(sourceName);
		int parenIndex = cleaned.indexOf('(');
		if (parenIndex > 0) {
			cleaned = cleaned.substring(0, parenIndex).trim();
		}

		cleaned = cleaned.replace('_', ' ').replace('-', ' ').trim();
		cleaned = cleaned.replaceAll("\\s+", " ");
		if (cleaned.isEmpty()) {
			return sourceName;
		}

		boolean hasUppercase = !cleaned.equals(cleaned.toLowerCase(Locale.ROOT));
		if (hasUppercase) {
			return cleaned;
		}

		String[] parts = cleaned.split(" ");
		StringBuilder builder = new StringBuilder(cleaned.length());
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.isEmpty()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(' ');
			}
			if (part.length() == 1) {
				builder.append(part.toUpperCase(Locale.ROOT));
			} else {
				builder.append(Character.toUpperCase(part.charAt(0)));
				builder.append(part.substring(1));
			}
		}
		return builder.isEmpty() ? sourceName : builder.toString();
	}

	private static boolean isSupportedStructureFile(Path path) {
		String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return lower.endsWith(".nbt")
			|| lower.endsWith(".schem")
			|| lower.endsWith(".schematic")
			|| lower.endsWith(".litematic");
	}

	private static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0) {
			return "";
		}
		return filename.substring(dot).toLowerCase(Locale.ROOT);
	}

	private static String baseName(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? filename : filename.substring(0, dot);
	}

	private static String sanitizeKey(String input) {
		String lowered = input.toLowerCase(Locale.ROOT);
		StringBuilder builder = new StringBuilder(lowered.length());
		for (int i = 0; i < lowered.length(); i++) {
			char c = lowered.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
				builder.append(c);
			} else if (c == ' ' || c == ':' || c == ',' || c == ';') {
				builder.append('_');
			}
		}
		return builder.isEmpty() ? "structure" : builder.toString();
	}

	private static String normalizeSourceToken(String input) {
		String token = sanitizeKey(stripDecorativeSuffixes(input));
		token = token.replaceAll("[_.-]+$", "");
		return token.isEmpty() ? "structure" : token;
	}

	private static String stripDecorativeSuffixes(String input) {
		String cleaned = input == null ? "" : input.trim();
		cleaned = cleaned.replaceAll("\\s*\\([^)]*\\)\\s*$", "");
		cleaned = cleaned.replaceAll("\\s*\\[[^]]*]\\s*$", "");
		return cleaned.trim();
	}

	private static OffsetToken parseOffsetToken(String stem) {
		Matcher xyz = OFFSET_XYZ_PATTERN.matcher(stem);
		if (xyz.matches()) {
			return new OffsetToken(
				cleanGroupName(xyz.group(1)),
				Integer.parseInt(xyz.group(2)),
				Integer.parseInt(xyz.group(3)),
				Integer.parseInt(xyz.group(4)),
				false
			);
		}

		Matcher csv = OFFSET_CSV_PATTERN.matcher(stem);
		if (csv.matches()) {
			return new OffsetToken(
				cleanGroupName(csv.group(1)),
				Integer.parseInt(csv.group(2)),
				Integer.parseInt(csv.group(3)),
				Integer.parseInt(csv.group(4)),
				false
			);
		}

		Matcher grid = OFFSET_GRID_PATTERN.matcher(stem);
		if (grid.matches()) {
			int x = Math.max(0, Integer.parseInt(grid.group(2)) - 1);
			int y = Math.max(0, romanToInt(grid.group(3).toLowerCase(Locale.ROOT)) - 1);
			int z = Character.toLowerCase(grid.group(4).charAt(0)) - 'a';
			return new OffsetToken(
				cleanGroupName(grid.group(1)),
				x,
				y,
				Math.max(0, z),
				true
			);
		}

		return null;
	}

	private static String cleanGroupName(String name) {
		String trimmed = name.trim();
		while (!trimmed.isEmpty()) {
			char end = trimmed.charAt(trimmed.length() - 1);
			if (end == '-' || end == '_' || end == ' ') {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			} else {
				break;
			}
		}
		return trimmed.isEmpty() ? "structure_pack" : trimmed;
	}

	private static int romanToInt(String roman) {
		int total = 0;
		int previous = 0;
		for (int i = roman.length() - 1; i >= 0; i--) {
			char c = roman.charAt(i);
			int value = switch (c) {
				case 'i' -> 1;
				case 'v' -> 5;
				case 'x' -> 10;
				case 'l' -> 50;
				case 'c' -> 100;
				default -> 0;
			};
			if (value < previous) {
				total -= value;
			} else {
				total += value;
				previous = value;
			}
		}
		return Math.max(1, total);
	}

	private static int bitsRequired(int paletteSize) {
		int bits = 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1));
		return Math.max(2, bits);
	}

	private static int readPackedIndex(long[] data, int bits, int blockIndex) {
		long bitIndex = (long) blockIndex * bits;
		int startLong = (int) (bitIndex >>> 6);
		int startOffset = (int) (bitIndex & 63);
		if (startLong >= data.length) {
			return 0;
		}

		long value = data[startLong] >>> startOffset;
		if (startOffset + bits > 64 && startLong + 1 < data.length) {
			value |= data[startLong + 1] << (64 - startOffset);
		}

		long mask = bits >= 63 ? -1L : (1L << bits) - 1L;
		return (int) (value & mask);
	}

	private static int[] decodeVarInts(byte[] input, int expectedCount) {
		int[] output = new int[expectedCount];
		int outputIndex = 0;
		int cursor = 0;

		while (cursor < input.length && outputIndex < expectedCount) {
			int value = 0;
			int shift = 0;
			byte current;
			do {
				if (cursor >= input.length || shift > 28) {
					return output;
				}

				current = input[cursor++];
				value |= (current & 0x7F) << shift;
				shift += 7;
			} while ((current & 0x80) != 0);

			output[outputIndex++] = value;
		}

		return output;
	}

	private static byte[] getByteArrayOrEmpty(CompoundTag tag, String key) {
		return tag.getByteArray(key).orElseGet(() -> new byte[0]);
	}

	private static long[] getLongArrayOrEmpty(CompoundTag tag, String key) {
		return tag.getLongArray(key).orElseGet(() -> new long[0]);
	}

	private static Vec3i readVec3(CompoundTag tag, String key) {
		CompoundTag compound = tag.getCompoundOrEmpty(key);
		if (!compound.isEmpty()) {
			int x = compound.getIntOr("x", compound.getIntOr("X", 0));
			int y = compound.getIntOr("y", compound.getIntOr("Y", 0));
			int z = compound.getIntOr("z", compound.getIntOr("Z", 0));
			return new Vec3i(x, y, z);
		}

		int[] intArray = tag.getIntArray(key).orElse(null);
		if (intArray != null && intArray.length >= 3) {
			return new Vec3i(intArray[0], intArray[1], intArray[2]);
		}

		ListTag list = tag.getListOrEmpty(key);
		if (!list.isEmpty()) {
			return new Vec3i(
				list.getIntOr(0, 0),
				list.getIntOr(1, 0),
				list.getIntOr(2, 0)
			);
		}

		return null;
	}

	private static Map<Vec3i, CompoundTag> readBlockEntityMap(
		ListTag blockEntityList,
		int baseX,
		int baseY,
		int baseZ,
		boolean includeLocalKeys
	) {
		Map<Vec3i, CompoundTag> map = new HashMap<>();
		for (int i = 0; i < blockEntityList.size(); i++) {
			CompoundTag tag = blockEntityList.getCompoundOrEmpty(i);
			Vec3i pos = readBlockEntityPos(tag);
			if (pos == null) {
				continue;
			}

			CompoundTag normalized = normalizeBlockEntityTag(tag);
			if (normalized == null) {
				continue;
			}

			Vec3i shifted = new Vec3i(pos.x() + baseX, pos.y() + baseY, pos.z() + baseZ);
			map.put(shifted, normalized.copy());
			if (includeLocalKeys) {
				map.put(new Vec3i(pos.x(), pos.y(), pos.z()), normalized.copy());
			}
		}
		return map;
	}

	private static Vec3i readBlockEntityPos(CompoundTag tag) {
		if (tag.contains("x") || tag.contains("y") || tag.contains("z")) {
			return new Vec3i(
				tag.getIntOr("x", 0),
				tag.getIntOr("y", 0),
				tag.getIntOr("z", 0)
			);
		}

		if (tag.contains("X") || tag.contains("Y") || tag.contains("Z")) {
			return new Vec3i(
				tag.getIntOr("X", 0),
				tag.getIntOr("Y", 0),
				tag.getIntOr("Z", 0)
			);
		}

		int[] posArray = tag.getIntArray("Pos").orElse(null);
		if (posArray != null && posArray.length >= 3) {
			return new Vec3i(posArray[0], posArray[1], posArray[2]);
		}

		ListTag posList = tag.getListOrEmpty("Pos");
		if (!posList.isEmpty()) {
			return new Vec3i(
				posList.getIntOr(0, 0),
				posList.getIntOr(1, 0),
				posList.getIntOr(2, 0)
			);
		}

		return null;
	}

	private static CompoundTag normalizeBlockEntityTag(CompoundTag source) {
		CompoundTag copy = source.copy();
		String id = copy.getStringOr("id", "");
		if (id.isEmpty()) {
			String fallbackId = copy.getStringOr("Id", "");
			if (!fallbackId.isEmpty()) {
				copy.putString("id", fallbackId);
				id = fallbackId;
			}
		}

		return id.isEmpty() ? null : copy;
	}

	private static CompoundTag copyNbt(CompoundTag nbt) {
		return nbt == null ? null : nbt.copy();
	}

	private static String normalizeBlockName(String blockName) {
		return blockName.contains(":") ? blockName : "minecraft:" + blockName;
	}

	private static BlockState readBlockStateFromString(String serializedState) {
		String stateText = serializedState.trim();
		if (stateText.isEmpty()) {
			return Blocks.AIR.defaultBlockState();
		}

		String blockName = stateText;
		String propertiesText = "";
		int bracket = stateText.indexOf('[');
		if (bracket >= 0 && stateText.endsWith("]")) {
			blockName = stateText.substring(0, bracket);
			propertiesText = stateText.substring(bracket + 1, stateText.length() - 1);
		}

		Identifier id = Identifier.tryParse(normalizeBlockName(blockName));
		Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
		BlockState state = block.defaultBlockState();

		if (!propertiesText.isEmpty()) {
			String[] entries = propertiesText.split(",");
			for (String entry : entries) {
				String[] pair = entry.split("=", 2);
				if (pair.length != 2) {
					continue;
				}

				String propertyName = pair[0].trim();
				String propertyValue = pair[1].trim();
				Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
				if (property == null) {
					continue;
				}
				state = applyProperty(state, property, propertyValue);
			}
		}

		return state;
	}

	private static BlockState readBlockState(CompoundTag stateTag) {
		String blockName = stateTag.getStringOr("Name", "minecraft:air");
		Identifier id = Identifier.tryParse(normalizeBlockName(blockName));
		Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
		BlockState state = block.defaultBlockState();
		CompoundTag properties = stateTag.getCompoundOrEmpty("Properties");

		for (String propertyName : properties.keySet()) {
			Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
			if (property == null) {
				continue;
			}

			String propertyValue = properties.getStringOr(propertyName, "");
			state = applyProperty(state, property, propertyValue);
		}

		return state;
	}

	private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String valueName) {
		return property.getValue(valueName)
			.map(value -> state.setValue(property, value))
			.orElse(state);
	}

	private static int stageFor(int x, int y, int z) {
		int wave = Math.floorMod(x * 5 - z * 3, 11);
		int radial = (int) Math.round(Math.sqrt(x * x + z * z) * 8);
		return y * 120 + radial + wave;
	}

	public record SelectionEntry(String key, Component displayName) {
	}

	public record ResolvedStructure(
		String key,
		Component displayName,
		int spawnDistance,
		boolean custom,
		List<GrandPalaceBlueprint.RelativeBlock> blueprint
	) {
	}

	private record ParsedPiece(
		String baseName,
		List<RawBlock> blocks
	) {
	}

	private record PieceWithOffset(
		ParsedPiece piece,
		OffsetToken offset
	) {
	}

	private record OffsetToken(
		String groupName,
		int x,
		int y,
		int z,
		boolean gridUnits
	) {
	}

	private record RawBlock(
		int x,
		int y,
		int z,
		BlockState state,
		CompoundTag blockEntityNbt
	) {
	}

	private record Vec3i(
		int x,
		int y,
		int z
	) {
	}

	private record ExternalStructure(
		String key,
		String displayName,
		int spawnDistance,
		List<GrandPalaceBlueprint.RelativeBlock> blueprint,
		String sourceToken
	) {
	}
}
