package dev.grandbuilder.build;

public final class HerobrineTiming {
	private HerobrineTiming() {
	}

	public static int cycleTicks(double requestedRate) {
		return cycleTicks(requestedRate, 0);
	}

	public static int originalCycleTicks(double requestedRate) {
		return (int) Math.max(2, Math.min(24, Math.round(12.0 / Math.sqrt(Math.max(0.01, requestedRate)))));
	}

	public static int cycleTicks(double requestedRate, int sequence) {
		int original = originalCycleTicks(requestedRate);
		return original / 2 + (original % 2 != 0 && (sequence & 1) != 0 ? 1 : 0);
	}

	public static double averageCycleTicks(double requestedRate) {
		return originalCycleTicks(requestedRate) / 2.0;
	}

	public static long estimateTicks(int blocks, double rate, int sequence) {
		if (blocks <= 0) return 0;
		return (blocks / 2L) * originalCycleTicks(rate) + (blocks % 2 == 0 ? 0 : cycleTicks(rate, sequence));
	}

	public static int contactTick(int duration) {
		return Math.max(1, duration - 2);
	}
}
