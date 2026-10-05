package dev.grandbuilder.build;

import dev.grandbuilder.build.AnimatedBuildManager.SnapshotBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;

// Sparse 16^3 sections retain state IDs; air is a valid snapshot, zero means absent.
final class CompactSnapshotMap extends AbstractMap<BlockPos, SnapshotBlock> {
	private final Long2ObjectOpenHashMap<int[]> sections = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<CompoundTag> entities = new Long2ObjectOpenHashMap<>();
	private final long budget;
	private long bytes;
	private int count;
	CompactSnapshotMap(long budget) { this.budget = budget; }
	private static long section(BlockPos pos) { return BlockPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4); }
	private static int index(BlockPos pos) { return ((pos.getY() & 15) * 16 + (pos.getZ() & 15)) * 16 + (pos.getX() & 15); }
	@Override public int size() { return count; }
	@Override public boolean containsKey(Object key) {
		if (!(key instanceof BlockPos pos)) return false;
		int[] states = sections.get(section(pos));
		return states != null && states[index(pos)] != 0;
	}
	@Override public SnapshotBlock get(Object key) {
		if (!(key instanceof BlockPos pos)) return null;
		int[] states = sections.get(section(pos));
		return states == null || states[index(pos)] == 0 ? null : new SnapshotBlock(Block.stateById(states[index(pos)] - 1), entities.get(pos.asLong()));
	}
	@Override public SnapshotBlock put(BlockPos pos, SnapshotBlock value) {
		SnapshotBlock old = get(pos);
		long key = section(pos);
		int[] states = sections.get(key);
		CompoundTag previous = entities.get(pos.asLong()), next = value.blockEntityNbt();
		long delta = (states == null ? 4096L * 4 + 128 : 0) + (next == null ? 0 : next.sizeInBytes() + 64L)
			- (previous == null ? 0 : previous.sizeInBytes() + 64L);
		if (bytes + delta > budget) throw new BudgetExceeded();
		if (states == null) { states = new int[4096]; sections.put(key, states); }
		if (old == null) count++;
		states[index(pos)] = Block.getId(value.state()) + 1;
		if (next == null) entities.remove(pos.asLong()); else entities.put(pos.asLong(), next);
		bytes += delta;
		return old;
	}
	long retainedBytes() { return bytes; }
	@Override public Set<Map.Entry<BlockPos, SnapshotBlock>> entrySet() {
		return new AbstractSet<>() {
			@Override public int size() { return count; }
			@Override public Iterator<Map.Entry<BlockPos, SnapshotBlock>> iterator() {
				return new Iterator<>() {
					private final Iterator<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<int[]>> iterator = sections.long2ObjectEntrySet().iterator();
					private long section;
					private int[] states;
					private int cursor;
					@Override public boolean hasNext() {
						while (true) {
							if (states != null) { while (cursor < 4096 && states[cursor] == 0) cursor++; if (cursor < 4096) return true; }
							if (!iterator.hasNext()) return false;
							var entry = iterator.next(); section = entry.getLongKey(); states = entry.getValue(); cursor = 0;
						}
					}
					@Override public Map.Entry<BlockPos, SnapshotBlock> next() {
						if (!hasNext()) throw new NoSuchElementException();
						int i = cursor++;
						BlockPos pos = new BlockPos((BlockPos.getX(section) << 4) + (i & 15), (BlockPos.getY(section) << 4) + i / 256,
							(BlockPos.getZ(section) << 4) + i / 16 % 16);
						return new SimpleImmutableEntry<>(pos, new SnapshotBlock(Block.stateById(states[i] - 1), entities.get(pos.asLong())));
					}
				};
			}
		};
	}
	static final class BudgetExceeded extends RuntimeException { private static final long serialVersionUID = 1L; }
}
