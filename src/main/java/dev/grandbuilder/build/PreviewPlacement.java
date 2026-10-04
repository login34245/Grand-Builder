package dev.grandbuilder.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record PreviewPlacement(BlockPos origin, Direction facing, BlockPos pivot) {
	public PreviewPlacement rotate() {
		int x = origin.getX() - pivot.getX(), z = origin.getZ() - pivot.getZ();
		return new PreviewPlacement(new BlockPos(pivot.getX() - z, origin.getY(), pivot.getZ() + x),
			facing.getClockWise(), pivot);
	}
	public PreviewPlacement move(Direction direction) {
		return move(direction, 1);
	}
	public PreviewPlacement move(Direction direction, int distance) {
		return new PreviewPlacement(origin.relative(direction, distance), facing, pivot.relative(direction, distance));
	}
}
