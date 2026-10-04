package dev.grandbuilder.build;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// Dense schematics retain palette indices, not a Java object for every air cell.
final class DenseStructureBlueprint extends AbstractList<GrandPalaceBlueprint.RelativeBlock> implements RandomAccess {
	private final int sourceWidth, sourceDepth, minX, minY, minZ, width, height, depth;
	private final byte[] bytes;
	private final short[] shorts;
	private final BlockState[] palette;
	private final int[] order;
	private final Long2ObjectOpenHashMap<CompoundTag> blockEntities;
	private final int nonAirCount;

	static DenseStructureBlueprint sponge(int sx, int sy, int sz, Map<Integer, BlockState> states, byte[] data,
		Map<Long, CompoundTag> entities, boolean preserveCrop, int positionLimit) throws IOException {
		int total = checkedVolume(sx, sy, sz, positionLimit);
		int paletteSize = states.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
		if (paletteSize <= 0 || paletteSize > 65536) throw new IOException("Invalid schematic palette size");
		int stateBytes = paletteSize <= 256 ? 1 : 2;
		long estimated = (long)total * (stateBytes + 4) + data.length + 4L * (sy * 120L + 8L * Math.max(sx, sz) + 32 + (long)sx * sz);
		if (estimated > memoryBudget()) throw new IOException("Schematic needs more than the safe decode memory budget (" + estimated / 1048576 + " MiB)");
		BlockState[] palette = new BlockState[paletteSize];
		for (var entry : states.entrySet()) {
			if (entry.getKey() < 0) throw new IOException("Negative palette index");
			palette[entry.getKey()] = entry.getValue();
		}
		byte[] bytes = stateBytes == 1 ? new byte[total] : null;
		short[] shorts = stateBytes == 2 ? new short[total] : null;
		int cursor = 0;
		for (int i = 0; i < total; i++) {
			int value = 0, shift = 0, next;
			do {
				if (cursor >= data.length || shift > 28) throw new IOException("Truncated or oversized schematic VarInt");
				next = data[cursor++] & 255;
				if (shift == 28 && (next & 0xF8) != 0) throw new IOException("Invalid schematic VarInt");
				value |= (next & 127) << shift;
				shift += 7;
			} while ((next & 128) != 0);
			if (value < 0 || value >= palette.length || palette[value] == null) throw new IOException("Unknown schematic palette index " + value);
			if (bytes != null) bytes[i] = (byte)value; else shorts[i] = (short)value;
		}
		if (cursor != data.length) throw new IOException("Schematic block data length does not match its dimensions");
		return new DenseStructureBlueprint(sx, sy, sz, palette, bytes, shorts, entities, preserveCrop);
	}

	static int checkedVolume(int x, int y, int z, int limit) throws IOException {
		if (x <= 0 || y <= 0 || z <= 0) throw new IOException("Invalid structure dimensions");
		long volume;
		try { volume = Math.multiplyExact(Math.multiplyExact((long)x, y), z); }
		catch (ArithmeticException exception) { throw new IOException("Structure dimensions overflow", exception); }
		if (volume > Math.min(Integer.MAX_VALUE - 8L, limit)) throw new IOException("Structure has " + volume + " positions, limit is " + limit);
		return (int)volume;
	}

	private static long memoryBudget() { return Math.min(256L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8); }

	private DenseStructureBlueprint(int sx, int sy, int sz, BlockState[] palette, byte[] bytes, short[] shorts,
		Map<Long, CompoundTag> entities, boolean preserveCrop) throws IOException {
		this.sourceWidth = sx; this.sourceDepth = sz; this.palette = palette; this.bytes = bytes; this.shorts = shorts;
		int ax = sx, ay = sy, az = sz, bx = -1, by = -1, bz = -1, solid = 0;
		boolean[] air = new boolean[palette.length];
		for (int i = 0; i < air.length; i++) air[i] = palette[i] == null || palette[i].isAir();
		int total = bytes != null ? bytes.length : shorts.length;
		for (int i = 0; i < total; i++) {
			if (air[paletteIndex(i)]) continue;
			solid++;
			int x = i % sx, z = i / sx % sz, y = i / (sx * sz);
			ax = Math.min(ax, x); ay = Math.min(ay, y); az = Math.min(az, z);
			bx = Math.max(bx, x); by = Math.max(by, y); bz = Math.max(bz, z);
		}
		if (solid == 0) throw new IOException("Structure contains no non-air blocks");
		if (preserveCrop) { ax = ay = az = 0; bx = sx - 1; by = sy - 1; bz = sz - 1; }
		this.minX = ax; this.minY = ay; this.minZ = az;
		this.width = bx - ax + 1; this.height = by - ay + 1; this.depth = bz - az + 1; this.nonAirCount = solid;
		this.blockEntities = new Long2ObjectOpenHashMap<>();
		entities.forEach((pos, tag) -> this.blockEntities.put((long)pos, tag.copy()));
		this.order = new int[width * height * depth];
		int maxStage = stage(width / 2 + 1, height - 1, depth / 2 + 1) + 12;
		if (maxStage < 0 || maxStage > 16_000_000) throw new IOException("Structure stage range exceeds safe memory budget");
		int[] histogram = new int[maxStage + 1];
		int[] floorStages = new int[width * depth];
		for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++)
			floorStages[z * width + x] = stage(x - width / 2, 0, z - depth / 2);
		for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++)
			histogram[y * 120 + floorStages[z * width + x]]++;
		int start = 0;
		for (int i = 0; i < histogram.length; i++) { int count = histogram[i]; histogram[i] = start; start += count; }
		for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
			int index = ((y + minY) * sourceDepth + z + minZ) * sourceWidth + x + minX;
			order[histogram[y * 120 + floorStages[z * width + x]]++] = index;
		}
	}

	private int paletteIndex(int sourceIndex) { return bytes != null ? bytes[sourceIndex] & 255 : shorts[sourceIndex] & 65535; }
	private static int stage(int x, int y, int z) { return y * 120 + (int)Math.round(Math.hypot(x, z) * 8) + Math.floorMod(x * 5 - z * 3, 11); }
	@Override public int size() { return order.length; }
	@Override public GrandPalaceBlueprint.RelativeBlock get(int index) {
		Objects.checkIndex(index, order.length);
		int source = order[index], sx = source % sourceWidth, sz = source / sourceWidth % sourceDepth, sy = source / (sourceWidth * sourceDepth);
		int x = sx - minX - width / 2, y = sy - minY, z = sz - minZ - depth / 2;
		CompoundTag nbt = blockEntities.isEmpty() ? null : blockEntities.get(BlockPos.asLong(sx, sy, sz));
		return new GrandPalaceBlueprint.RelativeBlock(x, y, z, palette[paletteIndex(source)], stage(x, y, z), nbt);
	}
	int width() { return width; }
	int height() { return height; }
	int depth() { return depth; }
	int nonAirCount() { return nonAirCount; }
	List<GrandPalaceBlueprint.RelativeBlock> sampleNonAir(int cap) {
		int stride = Math.max(1, (nonAirCount + cap - 1) / cap), solidIndex = 0;
		List<GrandPalaceBlueprint.RelativeBlock> sample = new ArrayList<>(Math.min(nonAirCount, cap));
		for (int i = 0; i < order.length; i++) {
			if (palette[paletteIndex(order[i])].isAir()) continue;
			if (solidIndex++ % stride == 0) sample.add(get(i));
		}
		return sample;
	}
	List<GrandPalaceBlueprint.RelativeBlock> boundaryBlocks() {
		List<GrandPalaceBlueprint.RelativeBlock> corners = new ArrayList<>(8);
		for (int x : new int[] {-width / 2, width - 1 - width / 2})
			for (int y : new int[] {0, height - 1})
				for (int z : new int[] {-depth / 2, depth - 1 - depth / 2})
					corners.add(new GrandPalaceBlueprint.RelativeBlock(x, y, z, Blocks.AIR.defaultBlockState(), 0, null));
		return corners;
	}
	long retainedBytes() { return (bytes != null ? bytes.length : (long)shorts.length * 2) + (long)order.length * 4; }
}
