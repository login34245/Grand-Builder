package dev.grandbuilder.build;

import dev.grandbuilder.config.GrandBuilderConfig;
import dev.grandbuilder.network.StructureListPayload;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.function.IntSupplier;
import java.nio.file.attribute.BasicFileAttributes;
import net.minecraft.core.BlockPos;
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
	private static final int MAX_DENSE_POSITIONS = 1_000_000_000;
	private static final int MAX_MATERIALIZED_POSITIONS = 2_000_000;

	private static final class Directories {
		static final Path PRIMARY = FabricLoader.getInstance().getGameDir().resolve("grand_builder").resolve("structures");
		static final Path SHARED = detectSharedStructuresDir();
		static final ExternalCatalog CATALOG = new ExternalCatalog(structureDirectories(), () -> GrandBuilderConfig.get().maxExternalStructureFiles);
	}
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
		return Directories.PRIMARY;
	}

	public static void clearExternalCache() {
		Directories.CATALOG.clear();
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

		List<ExternalSource> externalStructures = sortedExternalSources(externalStructuresCached().values());
		if (externalStructures.isEmpty()) {
			return listBuiltinSelections();
		}

		List<SelectionEntry> selections = new ArrayList<>();
		for (ExternalSource structure : externalStructures) {
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

		List<ExternalSource> externalStructures = sortedExternalSources(externalStructuresCached().values());
		if (!externalStructures.isEmpty()) {
			for (ExternalSource structure : externalStructures) {
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

		Map<String, ExternalSource> sources = externalStructuresCached();
		ExternalSource source = sources.get(key);
		if (source == null && !sources.isEmpty()) source = sortedExternalSources(sources.values()).get(0);
		if (source != null) {
			ExternalStructure external = Directories.CATALOG.resolve(source);
			return new ResolvedStructure(source.key(), Component.literal(source.displayName()),
				external == null ? 10 : external.spawnDistance(), false,
				external == null ? List.of() : external.blueprint());
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

	private static Map<String, ExternalSource> externalStructuresCached() {
		return GrandBuilderConfig.get().allowExternalStructures ? Directories.CATALOG.sources() : Map.of();
	}

	// Listing only stats files. Decoding uses a separate lock and retains at most one selected blueprint.
	static final class ExternalCatalog {
		private final List<Path> directories;
		private final IntSupplier fileLimit;
		private final Object indexLock = new Object(), decodeLock = new Object();
		private volatile Map<String, ExternalSource> index = Map.of();
		private volatile long expiresAt;
		private ExternalSource loadedSource;
		private ExternalStructure loaded;
		private final Map<ExternalSource, Boolean> failures = new LinkedHashMap<>();

		ExternalCatalog(List<Path> directories, IntSupplier fileLimit) {
			this.directories = List.copyOf(directories);
			this.fileLimit = fileLimit;
		}

		Map<String, ExternalSource> sources() {
			if (System.currentTimeMillis() < expiresAt) return index;
			synchronized (indexLock) {
				if (System.currentTimeMillis() < expiresAt) return index;
				index = scanSources(directories, Math.max(1, fileLimit.getAsInt()));
				expiresAt = System.currentTimeMillis() + EXTERNAL_CACHE_TTL_MILLIS;
				return index;
			}
		}

		void refresh() { expiresAt = 0; }
		void clear() {
			refresh();
			synchronized (decodeLock) { loadedSource = null; loaded = null; failures.clear(); }
		}

		ExternalStructure resolve(ExternalSource source) {
			synchronized (decodeLock) {
				if (source.equals(loadedSource)) return loaded;
				if (failures.containsKey(source)) return null;
				loaded = null;
				loadedSource = null;
				try {
					loaded = loadSource(source);
					if (loaded == null || loaded.blueprint().isEmpty()) throw new IOException("Structure contains no readable blocks");
					loadedSource = source;
					LOGGER.info("Loaded selected structure {} ({} positions)", source.displayName(), loaded.blueprint().size());
					return loaded;
				} catch (Exception exception) {
					loaded = null;
					failures.put(source, Boolean.TRUE);
					if (failures.size() > 64) failures.remove(failures.keySet().iterator().next());
					LOGGER.warn("Unable to load selected structure {}: {}", source.files(), exception.toString());
					return null;
				}
			}
		}
	}

	private static Map<String, ExternalSource> scanSources(List<Path> directories, int limit) {
		Map<String, ExternalSource> result = new LinkedHashMap<>();
		Map<String, List<FileStamp>> groups = new LinkedHashMap<>();
		int remaining = limit;
		for (Path directory : directories) {
			if (!Files.isDirectory(directory)) continue;
			try (var stream = Files.list(directory)) {
				for (Path path : stream.sorted().toList()) {
					if (remaining <= 0) break;
					if (Files.isDirectory(path)) {
						try (var files = Files.walk(path, 16)) {
							List<FileStamp> stamps = new ArrayList<>();
							for (Path file : files.filter(Files::isRegularFile).filter(StructureLibrary::isSupportedStructureFile)
								.limit(remaining).toList()) stamps.add(stamp(file));
							stamps.sort(Comparator.comparing(file -> file.path().toString()));
							remaining -= stamps.size();
							if (!stamps.isEmpty()) addSource(result, path.getFileName().toString(), stamps, true);
						}
					} else if (Files.isRegularFile(path) && isSupportedStructureFile(path)) {
						remaining--;
						FileStamp file = stamp(path);
						String name = baseName(path.getFileName().toString());
						OffsetToken offset = safeOffsetToken(name);
						if (offset == null) addSource(result, name, List.of(file), false);
						else groups.computeIfAbsent(offset.groupName(), ignored -> new ArrayList<>()).add(file);
					}
				}
			} catch (IOException exception) { LOGGER.warn("Unable to list structures in {}", directory, exception); }
		}
		for (var entry : groups.entrySet()) {
			List<FileStamp> files = entry.getValue();
			addSource(result, files.size() == 1 ? baseName(files.get(0).path().getFileName().toString()) : entry.getKey() + " (multi)",
				files, files.size() > 1);
		}
		return Collections.unmodifiableMap(result);
	}

	private static FileStamp stamp(Path path) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
		return new FileStamp(path, attrs.size(), attrs.lastModifiedTime());
	}

	private static OffsetToken safeOffsetToken(String name) {
		try { return parseOffsetToken(name); }
		catch (NumberFormatException exception) { return null; }
	}

	private static void addSource(Map<String, ExternalSource> result, String name, List<FileStamp> files, boolean pack) {
		String token = normalizeSourceToken(name), base = "file:" + token, key = base;
		for (int suffix = 2; result.containsKey(key); suffix++) key = base + "_" + suffix;
		result.put(key, new ExternalSource(key, prettifyExternalStructureName(name), token, name, List.copyOf(files), pack));
	}

	private static ExternalStructure loadSource(ExternalSource source) throws IOException {
		ExternalStructure structure;
		if (!source.pack()) {
			Path file = source.files().get(0).path();
			String extension = extensionOf(file.getFileName().toString());
			CompoundTag root = unwrapStructureRoot(readNbtAuto(file), extension);
			if (isSponge(root, extension) || extension.equals(".litematic")) {
				boolean preserveCrop = source.sourceToken().startsWith("world_");
				DenseStructureBlueprint blueprint = extension.equals(".litematic")
					? denseLitematic(root, preserveCrop) : denseSponge(root, preserveCrop);
				structure = new ExternalStructure(source.key(), source.displayName(),
					spawnDistance(blueprint.width(), blueprint.height(), blueprint.depth()), blueprint, source.sourceToken());
			} else {
				ParsedPiece piece = parsePiece(root, extension, source.sourceName());
				structure = piece == null ? null : buildExternalStructure(source.sourceName(), piece.blocks());
			}
		} else {
			List<RawBlock> plain = new ArrayList<>();
			List<PieceWithOffset> offsets = new ArrayList<>();
			int positions = 0;
			for (FileStamp file : source.files()) {
				ParsedPiece piece = parsePiece(file.path());
				if (piece == null) throw new IOException("Unreadable structure pack piece: " + file.path());
				positions = Math.addExact(positions, piece.blocks().size());
				checkMaterializedCount(positions);
				OffsetToken offset = safeOffsetToken(piece.baseName());
				if (offset == null) plain.addAll(piece.blocks());
				else offsets.add(new PieceWithOffset(piece, offset));
			}
			plain.addAll(combineOffsetPieces(offsets));
			structure = buildExternalStructure(source.sourceName(), dedupeByPosition(plain));
		}
		return structure == null ? null : new ExternalStructure(source.key(), source.displayName(), structure.spawnDistance(),
			structure.blueprint(), source.sourceToken());
	}

	private static int spawnDistance(int width, int height, int depth) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		int horizontal = Math.max(10, Math.max(width, depth) / 2 + config.spawnDistancePadding);
		horizontal = Math.max(config.minSpawnDistance, Math.min(config.maxSpawnDistance, horizontal));
		return Math.max(horizontal, Math.min(config.maxSpawnDistance, Math.max(config.minSpawnDistance, height / 2 + 8)));
	}

	private static boolean isSponge(CompoundTag root, String extension) {
		return (extension.equals(".schem") || extension.equals(".schematic"))
			&& (!root.getCompoundOrEmpty("Palette").isEmpty() || !root.getCompoundOrEmpty("Blocks").getCompoundOrEmpty("Palette").isEmpty());
	}

	static DenseStructureBlueprint denseSponge(CompoundTag root, boolean preserveCrop) throws IOException {
		CompoundTag section = root.getCompoundOrEmpty("Blocks");
		CompoundTag paletteTag = root.getCompoundOrEmpty("Palette");
		byte[] data = getByteArrayOrEmpty(root, "BlockData");
		if (paletteTag.isEmpty() || data.length == 0) {
			paletteTag = section.getCompoundOrEmpty("Palette");
			data = getByteArrayOrEmpty(section, "BlockData");
			if (data.length == 0) data = getByteArrayOrEmpty(section, "Data");
		}
		Map<Integer, BlockState> palette = new HashMap<>();
		for (String state : paletteTag.keySet()) {
			int id = paletteTag.getIntOr(state, -1);
			if (id < 0 || palette.put(id, readBlockStateFromString(state)) != null) throw new IOException("Invalid or duplicate palette ID");
		}
		ListTag tags = root.getListOrEmpty("BlockEntities");
		if (tags.isEmpty()) tags = section.getListOrEmpty("BlockEntities");
		Map<Long, CompoundTag> entities = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		int sx = root.getIntOr("Width", 0), sy = root.getIntOr("Height", 0), sz = root.getIntOr("Length", 0);
		readBlockEntityMap(tags, 0, 0, 0, false).forEach((pos, tag) -> {
			if (pos.x() >= 0 && pos.x() < sx && pos.y() >= 0 && pos.y() < sy && pos.z() >= 0 && pos.z() < sz)
				entities.put(((long)pos.y() * sz + pos.z()) * sx + pos.x(), tag);
		});
		return DenseStructureBlueprint.sponge(root.getIntOr("Width", 0), root.getIntOr("Height", 0),
			root.getIntOr("Length", 0), palette, data, entities, preserveCrop, MAX_DENSE_POSITIONS);
	}

	static DenseStructureBlueprint denseLitematic(CompoundTag root, boolean preserveCrop) throws IOException {
		CompoundTag tags = root.getCompoundOrEmpty("Regions");
		if (tags.isEmpty()) throw new IOException("Litematic has no regions");
		List<CompactRegion> regions = new ArrayList<>();
		List<BlockState> palette = new ArrayList<>(List.of(Blocks.AIR.defaultBlockState()));
		Map<BlockState, Integer> paletteIds = new HashMap<>();
		paletteIds.put(palette.getFirst(), 0);
		long ax = Long.MAX_VALUE, ay = ax, az = ax, bx = Long.MIN_VALUE, by = bx, bz = bx, inputBytes = 0;
		// Sorting names makes overlapping regions deterministic without allocating cell objects.
		for (String name : tags.keySet().stream().sorted().toList()) {
			CompoundTag region = tags.getCompoundOrEmpty(name);
			Vec3i size = readVec3(region, "Size"), pos = readVec3(region, "Position");
			if (size == null || pos == null || size.x() == Integer.MIN_VALUE || size.y() == Integer.MIN_VALUE || size.z() == Integer.MIN_VALUE)
				throw new IOException("Invalid litematic region dimensions");
			int sx = Math.abs(size.x()), sy = Math.abs(size.y()), sz = Math.abs(size.z());
			int volume = DenseStructureBlueprint.checkedVolume(sx, sy, sz, MAX_DENSE_POSITIONS);
			long x = (long)pos.x() + Math.min(0, size.x() + 1), y = (long)pos.y() + Math.min(0, size.y() + 1),
				z = (long)pos.z() + Math.min(0, size.z() + 1);
			ax = Math.min(ax, x); ay = Math.min(ay, y); az = Math.min(az, z);
			bx = Math.max(bx, x + sx); by = Math.max(by, y + sy); bz = Math.max(bz, z + sz);
			ListTag states = region.getListOrEmpty("BlockStatePalette");
			if (states.isEmpty() || states.size() > 65536) throw new IOException("Invalid litematic palette size");
			int[] remap = new int[states.size()];
			for (int i = 0; i < remap.length; i++) {
				BlockState state = readBlockState(states.getCompoundOrEmpty(i));
				Integer id = paletteIds.get(state);
				if (id == null) {
					if (palette.size() == 65536) throw new IOException("Combined litematic palette is too large");
					id = palette.size(); palette.add(state); paletteIds.put(state, id);
				}
				remap[i] = id;
			}
			long[] data = getLongArrayOrEmpty(region, "BlockStates");
			int bits = bitsRequired(states.size());
			if (data.length != ((long)volume * bits + 63) / 64) throw new IOException("Litematic block data length does not match its dimensions");
			inputBytes += (long)data.length * 8;
			regions.add(new CompactRegion(x, y, z, sx, sy, sz, volume, bits, remap, data, region.getListOrEmpty("TileEntities")));
		}
		if (bx - ax > Integer.MAX_VALUE || by - ay > Integer.MAX_VALUE || bz - az > Integer.MAX_VALUE)
			throw new IOException("Litematic region bounds overflow");
		int sx = (int)(bx - ax), sy = (int)(by - ay), sz = (int)(bz - az);
		int volume = DenseStructureBlueprint.checkedVolume(sx, sy, sz, MAX_DENSE_POSITIONS);
		DenseStructureBlueprint.checkMemory(sx, sy, sz, palette.size() <= 256 ? 1 : 2, inputBytes);
		byte[] bytes = palette.size() <= 256 ? new byte[volume] : null;
		short[] shorts = bytes == null ? new short[volume] : null;
		Map<Long, CompoundTag> entities = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		for (CompactRegion region : regions) {
			int ox = (int)(region.x() - ax), oy = (int)(region.y() - ay), oz = (int)(region.z() - az);
			// Native containers always run min-to-max, even when a region's signed Size is negative.
			if (!entities.isEmpty()) entities.keySet().removeIf(key -> {
				int x = (int)(key % sx), y = (int)(key / ((long)sx * sz)), z = (int)(key / sx % sz);
				return x >= ox && x < ox + region.sx() && y >= oy && y < oy + region.sy() && z >= oz && z < oz + region.sz();
			});
			for (int i = 0; i < region.volume(); i++) {
				int id = readPackedIndex(region.data(), region.bits(), i);
				if (id < 0 || id >= region.remap().length) throw new IOException("Unknown litematic palette index " + id);
				int target = ((i / (region.sx() * region.sz()) + oy) * sz + i / region.sx() % region.sz() + oz) * sx + i % region.sx() + ox;
				if (bytes != null) bytes[target] = (byte)region.remap()[id]; else shorts[target] = (short)region.remap()[id];
			}
			for (int i = 0; i < region.entities().size(); i++) {
				CompoundTag tag = region.entities().getCompoundOrEmpty(i);
				Vec3i pos = readBlockEntityPos(tag);
				if (pos == null || pos.x() < 0 || pos.x() >= region.sx() || pos.y() < 0 || pos.y() >= region.sy() || pos.z() < 0 || pos.z() >= region.sz()) continue;
				CompoundTag normalized = normalizeBlockEntityTag(tag);
				if (normalized != null) entities.put(((long)(pos.y() + oy) * sz + pos.z() + oz) * sx + pos.x() + ox, normalized);
			}
		}
		return new DenseStructureBlueprint(sx, sy, sz, palette.toArray(BlockState[]::new), bytes, shorts, entities, preserveCrop);
	}
	private record CompactRegion(long x, long y, long z, int sx, int sy, int sz, int volume, int bits,
		int[] remap, long[] data, ListTag entities) {}

	public static int countNonAir(List<GrandPalaceBlueprint.RelativeBlock> blueprint) {
		if (blueprint instanceof DenseStructureBlueprint dense) return dense.nonAirCount();
		int count = 0;
		for (var block : blueprint) if (!block.state().isAir()) count++;
		return count;
	}

	static List<GrandPalaceBlueprint.RelativeBlock> boundaryBlocks(List<GrandPalaceBlueprint.RelativeBlock> blueprint) {
		return blueprint instanceof DenseStructureBlueprint dense ? dense.boundaryBlocks() : blueprint;
	}

	private static void checkMaterializedCount(int count) throws IOException {
		int limit = (int)Math.min(MAX_MATERIALIZED_POSITIONS, Runtime.getRuntime().maxMemory() / 512);
		if (count > limit) throw new IOException("Structure format/pack exceeds safe object decode limit of " + limit + "; use a single Sponge .schem");
	}

	private static List<Path> structureDirectories() {
		List<Path> directories = new ArrayList<>(2);
		directories.add(Directories.PRIMARY);
		if (Directories.SHARED != null && !Directories.SHARED.equals(Directories.PRIMARY)) {
			directories.add(Directories.SHARED);
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

	private static List<RawBlock> combineOffsetPieces(List<PieceWithOffset> pieces) {
		List<RawBlock> combined = new ArrayList<>();
		for (PieceWithOffset piece : pieces) {
			int offsetX = piece.offset().gridUnits() ? Math.multiplyExact(piece.offset().x(), MULTI_NBT_STRIDE) : piece.offset().x();
			int offsetY = piece.offset().gridUnits() ? Math.multiplyExact(piece.offset().y(), MULTI_NBT_STRIDE) : piece.offset().y();
			int offsetZ = piece.offset().gridUnits() ? Math.multiplyExact(piece.offset().z(), MULTI_NBT_STRIDE) : piece.offset().z();
			for (RawBlock block : piece.piece().blocks()) {
				combined.add(new RawBlock(
					Math.addExact(block.x(), offsetX),
					Math.addExact(block.y(), offsetY),
					Math.addExact(block.z(), offsetZ),
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

	private static ParsedPiece parsePiece(Path path) throws IOException {
		String filename = path.getFileName().toString(), extension = extensionOf(filename);
		return parsePiece(unwrapStructureRoot(readNbtAuto(path), extension), extension, baseName(filename));
	}

	private static ParsedPiece parsePiece(CompoundTag root, String extension, String name) throws IOException {
		return switch (extension) {
			case ".nbt" -> parseVanillaStructure(root, name);
			case ".schem" -> parseSpongeStructure(root, name);
			case ".schematic" -> parseSchematicStructure(root, name);
			case ".litematic" -> parseLitematicStructure(root, name);
			default -> null;
		};
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

	static CompoundTag readNbtAuto(Path path) throws IOException {
		long budget = Math.min(128L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16);
		try (var input = new BufferedInputStream(Files.newInputStream(path))) {
			input.mark(2);
			boolean gzip = input.read() == 0x1f && input.read() == 0x8b;
			input.reset();
			NbtAccounter accounter = NbtAccounter.create(budget);
			return gzip ? NbtIo.readCompressed(input, accounter) : NbtIo.read(new DataInputStream(input), accounter);
		} catch (net.minecraft.nbt.NbtAccounterException exception) {
			throw new IOException("Structure NBT exceeds the safe read budget", exception);
		}
	}

	private static ParsedPiece parseVanillaStructure(CompoundTag root, String baseName) throws IOException {
		ListTag paletteTag = root.getListOrEmpty("palette");
		ListTag blocksTag = root.getListOrEmpty("blocks");
		if (paletteTag.isEmpty() || blocksTag.isEmpty()) {
			return null;
		}

		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(readBlockState(paletteTag.getCompoundOrEmpty(i)));
		}

		checkMaterializedCount(blocksTag.size());
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

	private static ParsedPiece parseSpongeStructure(CompoundTag root, String baseName) throws IOException {
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

		int total = DenseStructureBlueprint.checkedVolume(sizeX, sizeY, sizeZ, MAX_MATERIALIZED_POSITIONS);
		checkMaterializedCount(total);
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

	private static ParsedPiece parseSchematicStructure(CompoundTag root, String baseName) throws IOException {
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

		int total = DenseStructureBlueprint.checkedVolume(sizeX, sizeY, sizeZ, MAX_MATERIALIZED_POSITIONS);
		checkMaterializedCount(total);
		if (blocksArray.length != total) throw new IOException("Legacy schematic block data is truncated");
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

	private static ParsedPiece parseLitematicStructure(CompoundTag root, String baseName) throws IOException {
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
			if (rawSizeX == Integer.MIN_VALUE || rawSizeY == Integer.MIN_VALUE || rawSizeZ == Integer.MIN_VALUE)
				throw new IOException("Litematic dimensions overflow");
			int sizeX = Math.abs(rawSizeX);
			int sizeY = Math.abs(rawSizeY);
			int sizeZ = Math.abs(rawSizeZ);

			int posX, posY, posZ;
			try {
				posX = Math.addExact(position.x(), Math.min(0, rawSizeX + 1));
				posY = Math.addExact(position.y(), Math.min(0, rawSizeY + 1));
				posZ = Math.addExact(position.z(), Math.min(0, rawSizeZ + 1));
			} catch (ArithmeticException exception) { throw new IOException("Litematic region positions overflow", exception); }
			Map<Vec3i, CompoundTag> blockEntities = readBlockEntityMap(region.getListOrEmpty("TileEntities"), posX, posY, posZ, false);

			List<BlockState> palette = new ArrayList<>(paletteTag.size());
			for (int i = 0; i < paletteTag.size(); i++) {
				palette.add(readBlockState(paletteTag.getCompoundOrEmpty(i)));
			}
			if (palette.isEmpty()) {
				continue;
			}

			int bits = bitsRequired(palette.size());
			int total = DenseStructureBlueprint.checkedVolume(sizeX, sizeY, sizeZ, MAX_MATERIALIZED_POSITIONS);
			checkMaterializedCount(Math.addExact(blocks.size(), total));
			if ((long)packedStates.length * 64 < (long)total * bits) throw new IOException("Litematic block data is truncated");
			for (int index = 0; index < total; index++) {
				int paletteIndex = readPackedIndex(packedStates, bits, index);
				if (paletteIndex < 0 || paletteIndex >= palette.size()) {
					continue;
				}

				BlockState state = palette.get(paletteIndex);

				int x = index % sizeX;
				int z = (index / sizeX) % sizeZ;
				int y = index / (sizeX * sizeZ);

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
		boolean preserveCrop = sourceToken.startsWith("world_");

		for (RawBlock block : rawBlocks) {
			if (block.state().isAir() && !preserveCrop) {
				continue;
			}
			hasSolidBlocks |= !block.state().isAir();
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

	private static List<ExternalSource> sortedExternalSources(Iterable<ExternalSource> source) {
		List<ExternalSource> sorted = new ArrayList<>();
		for (ExternalSource structure : source) {
			sorted.add(structure);
		}
		sorted.sort(
			Comparator
				.comparingInt(StructureLibrary::externalOrderIndex)
				.thenComparing(ExternalSource::displayName, String::compareToIgnoreCase)
		);
		return sorted;
	}

	private static int externalOrderIndex(ExternalSource structure) {
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

	private static int[] decodeVarInts(byte[] input, int expectedCount) throws IOException {
		int[] output = new int[expectedCount];
		int outputIndex = 0;
		int cursor = 0;

		while (cursor < input.length && outputIndex < expectedCount) {
			int value = 0;
			int shift = 0;
			byte current;
			do {
				if (cursor >= input.length || shift > 28) {
					throw new IOException("Truncated or oversized schematic VarInt");
				}

				current = input[cursor++];
				if (shift == 28 && (current & 0xF8) != 0) throw new IOException("Invalid schematic VarInt");
				value |= (current & 0x7F) << shift;
				shift += 7;
			} while ((current & 0x80) != 0);

			output[outputIndex++] = value;
		}

		if (outputIndex != expectedCount || cursor != input.length) throw new IOException("Schematic block data length does not match dimensions");
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

	static BlockState readBlockState(CompoundTag stateTag) {
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
		int radial = (int) Math.round(Math.hypot(x, z) * 8);
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

	record FileStamp(Path path, long size, java.nio.file.attribute.FileTime modified) {}
	record ExternalSource(String key, String displayName, String sourceToken, String sourceName, List<FileStamp> files, boolean pack) {}
	record ExternalStructure(
		String key,
		String displayName,
		int spawnDistance,
		List<GrandPalaceBlueprint.RelativeBlock> blueprint,
		String sourceToken
	) {
	}
}
