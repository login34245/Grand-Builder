package dev.grandbuilder.build;

import java.util.Set;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class DismantleStateGuard {
	private static final Set<String> CONNECTIONS = Set.of("north", "east", "south", "west");

	private DismantleStateGuard() { }

	public static boolean matches(BlockState installed, BlockState current) {
		if (installed.getBlock() != current.getBlock()) return false;
		// Neighbor updates are not player edits; waterlogging and all other state still matter.
		for (Property<?> property : installed.getProperties()) {
			String name = property.getName();
			boolean derived = installed.getBlock() instanceof CrossCollisionBlock && CONNECTIONS.contains(name)
				|| installed.getBlock() instanceof WallBlock && (CONNECTIONS.contains(name) || name.equals("up"))
				|| installed.getBlock() instanceof FenceGateBlock && name.equals("in_wall");
			if (!derived && !installed.getValue(property).equals(current.getValue(property))) return false;
		}
		return true;
	}
}
