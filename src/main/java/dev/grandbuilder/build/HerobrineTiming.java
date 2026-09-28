package dev.grandbuilder.build;

public final class HerobrineTiming {
	private HerobrineTiming() {
	}

	public static int cycleTicks(double requestedRate) {
		return (int) Math.max(2, Math.min(24, Math.round(12.0 / Math.sqrt(Math.max(0.01, requestedRate)))));
	}

	public static int contactTick(int duration) {
		return Math.max(1, duration - 2);
	}
}
