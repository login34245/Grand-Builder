package dev.grandbuilder.network;

public enum FilmingAction {
	STATUS, INVISIBILITY_ON, INVISIBILITY_OFF, DAWN, DAY, EVENING, NIGHT, CLEAR, RAIN, THUNDER;

	public String translationKey() { return "screen.grand_builder.filming." + name().toLowerCase(java.util.Locale.ROOT); }
	public boolean changesTime() { return this == DAWN || this == DAY || this == EVENING || this == NIGHT; }
	public int dayTime() {
		return switch (this) { case DAWN -> 0; case DAY -> 6000; case EVENING -> 12000; case NIGHT -> 18000;
			default -> throw new IllegalStateException("Not a time action"); };
	}
	public static FilmingAction byId(int id) { return id >= 0 && id < values().length ? values()[id] : null; }
}
