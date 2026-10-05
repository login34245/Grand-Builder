package dev.grandbuilder.build;

import java.io.IOException;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

// Rank/select over one fluid bitmask gives two ordered views without block objects or int-per-cell lists.
final class DenseBuildPlan {
	private final DenseStructureBlueprint source;
	private final CompactStageOrder order;
	private final BuildStartSide side;
	private final long[] fluids;
	private final int[] prefix;
	private final int fluidCount;

	DenseBuildPlan(DenseStructureBlueprint source, BuildStartSide side) throws IOException {
		this.source = source;
		this.side = side;
		this.order = side == null ? new CompactStageOrder(source.width(), source.height(), source.depth(), true) : null;
		fluids = new long[(source.size() + 63) / 64];
		prefix = new int[(fluids.length + 15) / 16 + 1];
		int count = 0;
		for (int i = 0; i < source.size(); i++) {
			if (!source.stateAtFlat(flatIndex(i)).getFluidState().isEmpty()) { fluids[i >>> 6] |= 1L << (i & 63); count++; }
		}
		fluidCount = count;
		for (int group = 0; group < prefix.length - 1; group++) {
			int bits = 0;
			for (int word = group * 16; word < Math.min(fluids.length, group * 16 + 16); word++) bits += Long.bitCount(fluids[word]);
			prefix[group + 1] = prefix[group] + bits;
		}
	}
	private int flatIndex(int i) {
		int w = source.width(), h = source.height(), d = source.depth();
		if (side == null) return order.flatIndex(i);
		int x, y, z;
		switch (side) {
			case TOP -> { y = h - 1 - i / (w * d); x = i / d % w; z = i % d; }
			case FRONT, BACK -> { z = i / (h * w); if (side == BuildStartSide.BACK) z = d - 1 - z; y = i / w % h; x = i % w; }
			case LEFT, RIGHT -> { x = i / (h * d); if (side == BuildStartSide.RIGHT) x = w - 1 - x; y = i / d % h; z = i % d; }
			default -> throw new IllegalStateException();
		}
		return (y * d + z) * w + x;
	}
	List<GrandPalaceBlueprint.RelativeBlock> dry() { return new View(false); }
	List<GrandPalaceBlueprint.RelativeBlock> fluid() { return new View(true); }
	long retainedBytes() { return fluids.length * 8L + prefix.length * 4L + (order == null ? 0 : order.retainedBytes()); }
	private final class View extends AbstractList<GrandPalaceBlueprint.RelativeBlock> implements RandomAccess {
		private final boolean fluid;
		View(boolean fluid) { this.fluid = fluid; }
		@Override public int size() { return fluid ? fluidCount : source.size() - fluidCount; }
		private int rank(int group) { return fluid ? prefix[group] : Math.min(source.size(), group * 1024) - prefix[group]; }
		@Override public GrandPalaceBlueprint.RelativeBlock get(int index) {
			Objects.checkIndex(index, size());
			int low = 0, high = prefix.length - 1;
			while (low < high) { int mid = (low + high) >>> 1; if (rank(mid + 1) <= index) low = mid + 1; else high = mid; }
			int remaining = index - rank(low);
			for (int word = low * 16; word < fluids.length; word++) {
				long bits = fluid ? fluids[word] : ~fluids[word];
				if (word == fluids.length - 1 && (source.size() & 63) != 0) bits &= (1L << (source.size() & 63)) - 1;
				int count = Long.bitCount(bits);
				if (remaining >= count) { remaining -= count; continue; }
				while (remaining-- > 0) bits &= bits - 1;
				return source.atFlat(flatIndex(word * 64 + Long.numberOfTrailingZeros(bits)));
			}
			throw new IllegalStateException("Invalid compact build rank");
		}
	}
}
