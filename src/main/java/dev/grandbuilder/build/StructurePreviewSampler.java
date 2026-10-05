package dev.grandbuilder.build;

import dev.grandbuilder.network.StructurePreviewPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
		for (GrandPalaceBlueprint.RelativeBlock block : StructureLibrary.boundaryBlocks(blocks)) {
			BlockPos target = AnimatedBuildManager.transform(origin, facing, block);
			minX = Math.min(minX, target.getX()); maxX = Math.max(maxX, target.getX());
			minY = Math.min(minY, target.getY()); maxY = Math.max(maxY, target.getY());
			minZ = Math.min(minZ, target.getZ()); maxZ = Math.max(maxZ, target.getZ());
		}
		// Huge mostly-air files should spend the visual budget on solid blocks, not empty cells.
		List<GrandPalaceBlueprint.RelativeBlock> sample = blocks instanceof DenseStructureBlueprint dense
			? dense.sampleSurface(262_144) : blocks;
		if (sample != blocks) stride = 1;
		for (int index = 0; index < sample.size(); index += stride) {
			GrandPalaceBlueprint.RelativeBlock block = sample.get(index);
			if (!block.state().isAir()) {
				BlockPos target = AnimatedBuildManager.transform(origin, facing, block);
				visible.put(target.asLong(), AnimatedBuildManager.rotateState(block.state(), facing));
			}
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
		List<StructurePreviewPayload.Cell> sample;
		if (candidates.size() <= StructurePreviewPayload.MAX_CELLS) sample = candidates;
		else {
			// Keep the facade/roof continuous before spending the budget on interior detail.
			Map<Long, StructurePreviewPayload.Cell[]> xRays = new HashMap<>(), yRays = new HashMap<>(), zRays = new HashMap<>();
			for (var cell : candidates) {
				BlockPos pos = cell.target();
				outermost(xRays, pair(pos.getY(), pos.getZ()), cell, Direction.Axis.X);
				outermost(yRays, pair(pos.getX(), pos.getZ()), cell, Direction.Axis.Y);
				outermost(zRays, pair(pos.getX(), pos.getY()), cell, Direction.Axis.Z);
			}
			Set<Long> outer = new HashSet<>();
			for (var rays : List.of(xRays, yRays, zRays)) for (var ends : rays.values()) {
				outer.add(ends[0].target().asLong()); outer.add(ends[1].target().asLong());
			}
			List<StructurePreviewPayload.Cell> shell = new ArrayList<>(), interior = new ArrayList<>();
			for (var cell : candidates) (outer.contains(cell.target().asLong()) ? shell : interior).add(cell);
			sample = new ArrayList<>(StructurePreviewPayload.MAX_CELLS);
			appendSample(sample, shell);
			appendSample(sample, interior);
		}
		return new StructurePreviewPayload(kind, true, dimension, min, max, sample);
	}
	private static long pair(int a, int b) { return ((long)a << 32) | (b & 0xffffffffL); }
	private static void outermost(Map<Long, StructurePreviewPayload.Cell[]> rays, long key, StructurePreviewPayload.Cell cell, Direction.Axis axis) {
		var ends = rays.computeIfAbsent(key, ignored -> new StructurePreviewPayload.Cell[] {cell, cell});
		int coordinate = cell.target().get(axis);
		if (coordinate < ends[0].target().get(axis)) ends[0] = cell;
		if (coordinate > ends[1].target().get(axis)) ends[1] = cell;
	}
	private static void appendSample(List<StructurePreviewPayload.Cell> result, List<StructurePreviewPayload.Cell> source) {
		int budget = StructurePreviewPayload.MAX_CELLS - result.size();
		if (budget <= 0) return;
		int count = Math.min(budget, source.size());
		for (int i = 0; i < count; i++) result.add(source.get((int)((long)i * source.size() / count)));
	}
}
