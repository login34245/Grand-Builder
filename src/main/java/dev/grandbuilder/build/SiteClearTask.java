package dev.grandbuilder.build;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public final class SiteClearTask {
	public enum Cell { EMPTY, OCCUPIED, OUTSIDE, UNLOADED }
	public enum Status { WORKING, WAITING, DONE, LIMIT }
	private final SiteClearVolume volume;
	private long cursor;
	private int occupied;
	private boolean checked, done, refused;
	public SiteClearTask(SiteClearVolume volume) { this.volume = volume; }
	public int occupied() { return occupied; }
	public boolean done() { return done; }
	public long remainingScans() { return done ? 0 : volume.size()-cursor+(checked?0:volume.size()); }
	public long estimateTicks() { return volume.estimateTicks(remainingScans()) + (done?0:(occupied+511L)/512); }
	public Status step(int changeBudget, int snapshotLimit, long deadline, Function<BlockPos, Cell> inspect, Predicate<BlockPos> remove) {
		if (done) return Status.DONE;
		if (refused) return Status.LIMIT;
		int changes = 0;
		for (int scanned=0; scanned<4096 && cursor<volume.size(); scanned++) {
			BlockPos position = volume.position(cursor);
			Cell cell = inspect.apply(position);
			if (cell == Cell.UNLOADED) return Status.WAITING;
			if (cell == Cell.OCCUPIED) {
				if (!checked) {
					if (++occupied > snapshotLimit) { refused=true; return Status.LIMIT; }
				} else {
					if (!remove.test(position)) { refused=true; return Status.LIMIT; }
					changes++;
				}
			}
			cursor++;
			if (changes >= Math.max(1,changeBudget) || System.nanoTime() >= deadline) break;
		}
		if (cursor < volume.size()) return Status.WORKING;
		if (!checked) { checked=true; cursor=0; return Status.WORKING; }
		done=true;
		return Status.DONE;
	}
}
