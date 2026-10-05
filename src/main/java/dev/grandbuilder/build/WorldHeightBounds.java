package dev.grandbuilder.build;

import net.minecraft.world.level.LevelHeightAccessor;

public final class WorldHeightBounds {
	private WorldHeightBounds() { }
	public static boolean contains(LevelHeightAccessor world, int minY, int maxY) {
		return minY <= maxY && minY >= world.getMinY() && maxY < world.getMaxY();
	}
}
