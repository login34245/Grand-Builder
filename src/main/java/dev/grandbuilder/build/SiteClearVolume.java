package dev.grandbuilder.build;

import net.minecraft.core.BlockPos;

/** A bounded top-down cursor, without allocating an air block for every position. */
public record SiteClearVolume(BlockPos min, BlockPos max) {
	public SiteClearVolume {
		if (min.getX()>max.getX() || min.getY()>max.getY() || min.getZ()>max.getZ())
			throw new IllegalArgumentException("Invalid clearing bounds");
		min=min.immutable();max=max.immutable();
	}
	public long size() { return (long) width() * height() * depth(); }
	private int width() { return max.getX() - min.getX() + 1; }
	private int height() { return max.getY() - min.getY() + 1; }
	private int depth() { return max.getZ() - min.getZ() + 1; }
	public BlockPos position(long index) {
		if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();
		long layer = (long) width() * depth();
		return new BlockPos(min.getX() + (int) (index % width()), max.getY() - (int) (index / layer),
			min.getZ() + (int) (index / width() % depth()));
	}
	public long estimateTicks(long remaining) { return (Math.max(0, remaining) + 4095) / 4096; }
}
