package dev.grandbuilder.build;

import dev.grandbuilder.GrandBuilderMod;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class CustomStructureExporter {
	private static final int NBT_PIECE_SIZE = 48;
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private CustomStructureExporter() {
	}

	public static ExportResult export(
		ServerPlayer player,
		List<GrandPalaceBlueprint.RelativeBlock> blocks,
		CustomCaptureFormat format
	) throws IOException {
		if (!format.writesFiles() || blocks.isEmpty()) {
			return new ExportResult(format, 0, StructureLibrary.structuresDirectory());
		}

		Path directory = StructureLibrary.structuresDirectory();
		Files.createDirectories(directory);
		String baseName = "custom_" + sanitize(player.getName().getString()) + "_" + LocalDateTime.now().format(FILE_TIMESTAMP);
		NormalizedBlocks normalized = normalize(blocks);

		return switch (format) {
			case NBT_SINGLE -> {
				Path file = directory.resolve(baseName + ".nbt");
				writeVanillaNbt(file, normalized.blocks(), normalized.width(), normalized.height(), normalized.depth());
				yield new ExportResult(format, 1, file);
			}
			case NBT_PIECES -> writeVanillaNbtPieces(directory, baseName, normalized);
			case SCHEM_SINGLE -> {
				Path file = directory.resolve(baseName + ".schem");
				writeSpongeSchem(file, normalized);
				yield new ExportResult(format, 1, file);
			}
			case RUNTIME -> new ExportResult(format, 0, directory);
		};
	}

	private static ExportResult writeVanillaNbtPieces(Path directory, String baseName, NormalizedBlocks normalized) throws IOException {
		Map<PieceKey, List<GrandPalaceBlueprint.RelativeBlock>> pieces = new HashMap<>();
		for (GrandPalaceBlueprint.RelativeBlock block : normalized.blocks()) {
			int pieceX = Math.floorDiv(block.x(), NBT_PIECE_SIZE);
			int pieceY = Math.floorDiv(block.y(), NBT_PIECE_SIZE);
			int pieceZ = Math.floorDiv(block.z(), NBT_PIECE_SIZE);
			int offsetX = pieceX * NBT_PIECE_SIZE;
			int offsetY = pieceY * NBT_PIECE_SIZE;
			int offsetZ = pieceZ * NBT_PIECE_SIZE;
			pieces.computeIfAbsent(new PieceKey(offsetX, offsetY, offsetZ), ignored -> new ArrayList<>())
				.add(new GrandPalaceBlueprint.RelativeBlock(
					block.x() - offsetX,
					block.y() - offsetY,
					block.z() - offsetZ,
					block.state(),
					block.stage(),
					copyNbt(block.blockEntityNbt())
				));
		}

		List<Map.Entry<PieceKey, List<GrandPalaceBlueprint.RelativeBlock>>> orderedPieces = new ArrayList<>(pieces.entrySet());
		orderedPieces.sort(Comparator
			.comparingInt((Map.Entry<PieceKey, List<GrandPalaceBlueprint.RelativeBlock>> entry) -> entry.getKey().y())
			.thenComparingInt(entry -> entry.getKey().x())
			.thenComparingInt(entry -> entry.getKey().z()));

		Path firstFile = directory;
		int written = 0;
		for (Map.Entry<PieceKey, List<GrandPalaceBlueprint.RelativeBlock>> entry : orderedPieces) {
			PieceKey key = entry.getKey();
			List<GrandPalaceBlueprint.RelativeBlock> pieceBlocks = entry.getValue();
			Bounds bounds = boundsOf(pieceBlocks);
			Path file = directory.resolve(baseName + "_x" + key.x() + "_y" + key.y() + "_z" + key.z() + ".nbt");
			writeVanillaNbt(file, pieceBlocks, bounds.width(), bounds.height(), bounds.depth());
			if (written == 0) {
				firstFile = file;
			}
			written++;
		}

		return new ExportResult(CustomCaptureFormat.NBT_PIECES, written, firstFile);
	}

	private static void writeVanillaNbt(
		Path file,
		List<GrandPalaceBlueprint.RelativeBlock> blocks,
		int width,
		int height,
		int depth
	) throws IOException {
		Palette palette = buildPalette(blocks);
		CompoundTag root = new CompoundTag();
		root.put("size", intList(width, height, depth));
		root.put("palette", palette.paletteTag());
		root.put("blocks", vanillaBlocksTag(blocks, palette));
		root.put("entities", new ListTag());
		root.putInt("DataVersion", 3955);
		NbtIo.writeCompressed(root, file);
	}

	private static ListTag vanillaBlocksTag(List<GrandPalaceBlueprint.RelativeBlock> blocks, Palette palette) {
		ListTag blocksTag = new ListTag();
		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			CompoundTag blockTag = new CompoundTag();
			blockTag.put("pos", intList(block.x(), block.y(), block.z()));
			blockTag.putInt("state", palette.indexOf(block.state()));
			if (block.blockEntityNbt() != null && !block.blockEntityNbt().isEmpty()) {
				blockTag.put("nbt", localizeBlockEntity(block.blockEntityNbt(), block.x(), block.y(), block.z()));
			}
			blocksTag.add(blockTag);
		}
		return blocksTag;
	}

	private static void writeSpongeSchem(Path file, NormalizedBlocks normalized) throws IOException {
		Palette palette = buildPalette(normalized.blocks());
		byte[] blockData = spongeBlockData(normalized, palette);

		CompoundTag root = new CompoundTag();
		root.putInt("Version", 2);
		root.putInt("DataVersion", 3955);
		root.putShort("Width", (short) normalized.width());
		root.putShort("Height", (short) normalized.height());
		root.putShort("Length", (short) normalized.depth());
		root.putInt("PaletteMax", palette.size());
		root.put("Palette", palette.spongePaletteTag());
		root.putByteArray("BlockData", blockData);
		root.put("BlockEntities", spongeBlockEntitiesTag(normalized.blocks()));
		NbtIo.writeCompressed(root, file);
	}

	private static byte[] spongeBlockData(NormalizedBlocks normalized, Palette palette) {
		int total = normalized.width() * normalized.height() * normalized.depth();
		int[] indexes = new int[total];
		for (GrandPalaceBlueprint.RelativeBlock block : normalized.blocks()) {
			int index = block.x()
				+ block.z() * normalized.width()
				+ block.y() * normalized.width() * normalized.depth();
			if (index >= 0 && index < indexes.length) {
				indexes[index] = palette.indexOf(block.state());
			}
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream(indexes.length);
		for (int index : indexes) {
			writeVarInt(out, index);
		}
		return out.toByteArray();
	}

	private static ListTag spongeBlockEntitiesTag(List<GrandPalaceBlueprint.RelativeBlock> blocks) {
		ListTag blockEntities = new ListTag();
		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			if (block.blockEntityNbt() == null || block.blockEntityNbt().isEmpty()) {
				continue;
			}
			blockEntities.add(localizeBlockEntity(block.blockEntityNbt(), block.x(), block.y(), block.z()));
		}
		return blockEntities;
	}

	private static Palette buildPalette(List<GrandPalaceBlueprint.RelativeBlock> blocks) {
		Map<String, Integer> indexesByState = new LinkedHashMap<>();
		List<BlockState> states = new ArrayList<>();
		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			String serialized = serializeState(block.state());
			if (indexesByState.containsKey(serialized)) {
				continue;
			}
			indexesByState.put(serialized, states.size());
			states.add(block.state());
		}
		return new Palette(indexesByState, states);
	}

	private static NormalizedBlocks normalize(List<GrandPalaceBlueprint.RelativeBlock> blocks) {
		Bounds bounds = boundsOf(blocks);
		List<GrandPalaceBlueprint.RelativeBlock> normalized = new ArrayList<>(blocks.size());
		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			normalized.add(new GrandPalaceBlueprint.RelativeBlock(
				block.x() - bounds.minX(),
				block.y() - bounds.minY(),
				block.z() - bounds.minZ(),
				block.state(),
				block.stage(),
				copyNbt(block.blockEntityNbt())
			));
		}
		normalized.sort(Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage));
		return new NormalizedBlocks(normalized, bounds.width(), bounds.height(), bounds.depth());
	}

	private static Bounds boundsOf(List<GrandPalaceBlueprint.RelativeBlock> blocks) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (GrandPalaceBlueprint.RelativeBlock block : blocks) {
			minX = Math.min(minX, block.x());
			minY = Math.min(minY, block.y());
			minZ = Math.min(minZ, block.z());
			maxX = Math.max(maxX, block.x());
			maxY = Math.max(maxY, block.y());
			maxZ = Math.max(maxZ, block.z());
		}
		return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static CompoundTag writeBlockStateTag(BlockState state) {
		CompoundTag tag = new CompoundTag();
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		tag.putString("Name", id == null ? "minecraft:air" : id.toString());
		if (!state.getProperties().isEmpty()) {
			CompoundTag properties = new CompoundTag();
			for (Property<?> property : state.getProperties()) {
				properties.putString(property.getName(), propertyValueName(state, property));
			}
			tag.put("Properties", properties);
		}
		return tag;
	}

	private static String serializeState(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		StringBuilder builder = new StringBuilder(id == null ? "minecraft:air" : id.toString());
		if (!state.getProperties().isEmpty()) {
			builder.append('[');
			boolean first = true;
			for (Property<?> property : state.getProperties()) {
				if (!first) {
					builder.append(',');
				}
				first = false;
				builder.append(property.getName()).append('=').append(propertyValueName(state, property));
			}
			builder.append(']');
		}
		return builder.toString();
	}

	private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static ListTag intList(int x, int y, int z) {
		ListTag list = new ListTag();
		list.add(IntTag.valueOf(x));
		list.add(IntTag.valueOf(y));
		list.add(IntTag.valueOf(z));
		return list;
	}

	private static CompoundTag localizeBlockEntity(CompoundTag source, int x, int y, int z) {
		CompoundTag copy = source.copy();
		copy.putInt("x", x);
		copy.putInt("y", y);
		copy.putInt("z", z);
		return copy;
	}

	private static CompoundTag copyNbt(CompoundTag source) {
		return source == null ? null : source.copy();
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		int remaining = value;
		while ((remaining & -128) != 0) {
			out.write(remaining & 127 | 128);
			remaining >>>= 7;
		}
		out.write(remaining);
	}

	private static String sanitize(String name) {
		String lowered = name.toLowerCase(Locale.ROOT);
		StringBuilder builder = new StringBuilder(lowered.length());
		for (int i = 0; i < lowered.length(); i++) {
			char c = lowered.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
				builder.append(c);
			} else {
				builder.append('_');
			}
		}
		return builder.isEmpty() ? "player" : builder.toString();
	}

	public record ExportResult(CustomCaptureFormat format, int filesWritten, Path path) {
	}

	private record NormalizedBlocks(List<GrandPalaceBlueprint.RelativeBlock> blocks, int width, int height, int depth) {
	}

	private record PieceKey(int x, int y, int z) {
	}

	private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		private int width() {
			return maxX - minX + 1;
		}

		private int height() {
			return maxY - minY + 1;
		}

		private int depth() {
			return maxZ - minZ + 1;
		}
	}

	private record Palette(Map<String, Integer> indexesByState, List<BlockState> states) {
		private int indexOf(BlockState state) {
			Integer index = indexesByState.get(serializeState(state));
			if (index == null) {
				GrandBuilderMod.LOGGER.warn("Missing palette entry for captured state {}", state);
				return 0;
			}
			return index;
		}

		private int size() {
			return states.size();
		}

		private ListTag paletteTag() {
			ListTag tag = new ListTag();
			for (BlockState state : states) {
				tag.add(writeBlockStateTag(state));
			}
			return tag;
		}

		private CompoundTag spongePaletteTag() {
			CompoundTag palette = new CompoundTag();
			for (Map.Entry<String, Integer> entry : indexesByState.entrySet()) {
				palette.putInt(entry.getKey(), entry.getValue());
			}
			return palette;
		}
	}
}
