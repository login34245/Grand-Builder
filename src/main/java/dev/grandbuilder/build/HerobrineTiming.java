package dev.grandbuilder.build;

public final class HerobrineTiming {
	public static final int MAX_WINDUP_TICKS = 24;
	public static final int RECOVERY_TICKS = 3;

	private HerobrineTiming() {
	}

	public static int contactTick(int duration) {
		// Visual recovery is client-only and must never delay the next build cycle.
		return Math.max(1, duration);
	}
}
