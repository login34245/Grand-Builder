package dev.grandbuilder.build;

import java.io.IOException;
import java.util.Objects;

// A stage is y * 120 + a column's floor stage. Store columns once, not every cell.
final class CompactStageOrder {
	private final int width, depth, size;
	private final boolean xFirst;
	private final int[] columns, floorStages, offsets, starts;

	CompactStageOrder(int width, int height, int depth, boolean xFirst) throws IOException {
		this.width = width;
		this.depth = depth;
		this.xFirst = xFirst;
		this.size = DenseStructureBlueprint.checkedVolume(width, height, depth, Integer.MAX_VALUE - 8);
		int area = Math.multiplyExact(width, depth);
		floorStages = new int[area];
		int maxFloor = 0;
		for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
			int stage = floorStage(x - width / 2, z - depth / 2);
			floorStages[z * width + x] = stage;
			maxFloor = Math.max(maxFloor, stage);
		}
		long maxStage = (height - 1L) * 120 + maxFloor;
		if (maxStage > 16_000_000) throw new IOException("Structure stage range exceeds safe memory budget");
		int[] counts = new int[maxFloor + 1];
		for (int floor : floorStages) counts[floor]++;
		int[] residues = new int[121];
		for (int floor = 0; floor < counts.length; floor++) residues[floor % 120 + 1] += counts[floor];
		for (int r = 1; r < residues.length; r++) residues[r] += residues[r - 1];
		int[] destinations = new int[counts.length];
		int[] cursor = residues.clone();
		for (int floor = maxFloor; floor >= 0; floor--) {
			destinations[floor] = cursor[floor % 120];
			cursor[floor % 120] += counts[floor];
		}
		columns = new int[area];
		for (int i = 0; i < area; i++) {
			int column = xFirst ? (i % depth) * width + i / depth : i;
			columns[destinations[floorStages[column]]++] = column;
		}
		offsets = new int[(int)maxStage + 2];
		starts = new int[(int)maxStage + 1];
		for (int stage = 0; stage <= maxStage; stage++) {
			int r = stage % 120;
			int low = firstAtMost(residues[r], residues[r + 1], stage);
			int high = firstAtMost(residues[r], residues[r + 1], stage - (height - 1L) * 120 - 1);
			starts[stage] = low;
			offsets[stage + 1] = offsets[stage] + high - low;
		}
		if (offsets[offsets.length - 1] != size) throw new IOException("Invalid compact stage ordering");
	}
	private int firstAtMost(int low, int high, long threshold) {
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (floorStages[columns[middle]] > threshold) low = middle + 1; else high = middle;
		}
		return low;
	}

	static int floorStage(int x, int z) {
		return (int)Math.round(Math.hypot(x, z) * 8) + Math.floorMod(x * 5 - z * 3, 11);
	}
	int flatIndex(int index) {
		Objects.checkIndex(index, size);
		int low = 0, high = starts.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (offsets[middle + 1] <= index) low = middle + 1; else high = middle;
		}
		int column = columns[starts[low] + index - offsets[low]];
		return ((low - floorStages[column]) / 120) * width * depth + column;
	}
	int orderedIndex(int flat) {
		Objects.checkIndex(flat, size);
		int column = flat % (width * depth), floor = floorStages[column], stage = flat / (width * depth) * 120 + floor;
		int low = starts[stage], high = low + offsets[stage + 1] - offsets[stage];
		int ordinal = xFirst ? column % width * depth + column / width : column;
		while (low < high) {
			int mid = (low + high) >>> 1, other = columns[mid], otherFloor = floorStages[other];
			int otherOrdinal = xFirst ? other % width * depth + other / width : other;
			if (otherFloor > floor || otherFloor == floor && otherOrdinal < ordinal) low = mid + 1; else high = mid;
		}
		return offsets[stage] + low - starts[stage];
	}
	long retainedBytes() { return 4L * (columns.length + floorStages.length + offsets.length + starts.length); }
}
