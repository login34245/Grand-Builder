package dev.grandbuilder.build;

public final class EuropaTimeline {
	public static final int JUPITER = 60, EUROPA = 128, STATION = 212, FIRE = 280, CONTACT = 338;
	public static final int ARRIVAL = 360, FLASH = 100, AFTERMATH = 120;
	private EuropaTimeline() { }
	public static int shot(double age) {
		return age < JUPITER ? 0 : age < EUROPA ? 1 : age < STATION ? 2 : age < FIRE ? 3 : 4;
	}
	public static double flashAlpha(double revealAge) {
		if (revealAge < 0 || revealAge >= FLASH) return 0;
		double t = Math.clamp((revealAge - 72) / 28, 0, 1);
		return 1 - t * t * (3 - 2 * t);
	}
}
