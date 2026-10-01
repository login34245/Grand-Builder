package dev.grandbuilder.build;

import dev.grandbuilder.network.StructurePreviewPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class StructurePreviewSampler {
	private StructurePreviewSampler() { }
	public static StructurePreviewPayload blueprint(int kind, Identifier dimension, BlockPos origin, Direction facing,
		List<GrandPalaceBlueprint.RelativeBlock> blocks) {
		Map<Long, BlockState> visible = new HashMap<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		int stride = Math.max(1, (blocks.size() + 999999) / 1000000);
		for (int index = 0; index < blocks.size(); index++) {
			GrandPalaceBlueprint.RelativeBlock block = blocks.get(index);
			BlockPos target = AnimatedBuildManager.transform(origin, facing, block);
			minX = Math.min(minX, target.getX()); maxX = Math.max(maxX, target.getX());
			minY = Math.min(minY, target.getY()); maxY = Math.max(maxY, target.getY());
			minZ = Math.min(minZ, target.getZ()); maxZ = Math.max(maxZ, target.getZ());
			if (index % stride == 0 && !block.state().isAir())
				visible.put(target.asLong(), AnimatedBuildManager.rotateState(block.state(), facing));
		}
		if (blocks.isEmpty()) return new StructurePreviewPayload(kind, false, dimension, origin, origin, List.of());
		return fromStates(kind, dimension, new BlockPos(minX,minY,minZ), new BlockPos(maxX,maxY,maxZ), visible);
	}
	public static StructurePreviewPayload fromStates(int kind, Identifier dimension, BlockPos min, BlockPos max,
		Map<Long, BlockState> states) {
		List<StructurePreviewPayload.Cell> candidates = new ArrayList<>();
		for (Map.Entry<Long, BlockState> entry : states.entrySet()) {
			BlockState state = entry.getValue();
			if (state.isAir()) continue;
			BlockPos pos = BlockPos.of(entry.getKey());
			if (pos.getX() < min.getX() || pos.getX() > max.getX() || pos.getY() < min.getY() || pos.getY() > max.getY()
				|| pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) continue;
			boolean enclosed = state.isSolidRender();
			if (enclosed) for (Direction direction : Direction.values()) {
				BlockState neighbor = states.get(pos.relative(direction).asLong());
				if (neighbor == null || !neighbor.isSolidRender()) { enclosed = false; break; }
			}
			if (!enclosed) candidates.add(new StructurePreviewPayload.Cell(pos, Block.getId(state)));
		}
		candidates.sort((a,b) -> {
			int byY = Integer.compare(a.target().getY(), b.target().getY());
			if (byY != 0) return byY;
			int byX = Integer.compare(a.target().getX(), b.target().getX());
			return byX != 0 ? byX : Integer.compare(a.target().getZ(), b.target().getZ());
		});
		int step = Math.max(1, (candidates.size() + StructurePreviewPayload.MAX_CELLS - 1) / StructurePreviewPayload.MAX_CELLS);
		List<StructurePreviewPayload.Cell> sample = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i += step) sample.add(candidates.get(i));
		return new StructurePreviewPayload(kind, true, dimension, min, max, sample);
	}
}
