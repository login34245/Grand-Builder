package dev.grandbuilder.build;

public final class BuildCadence {
	private BuildCadence() {
	}

	public static int budget(int requested, int limit) {
		return Math.min(Math.max(1, requested), Math.max(1, limit));
	}

	public static long estimateTicks(int blocks, int requested, int delay, int limit, int elapsed) {
		if (blocks <= 0) return 0;
		int cycleDelay = Math.max(1, delay);
		int cycleBudget = budget(requested, limit);
		long cycles = (blocks + (long) cycleBudget - 1) / cycleBudget;
		return cycles * cycleDelay - Math.max(0, Math.min(elapsed, cycleDelay - 1));
	}
}
