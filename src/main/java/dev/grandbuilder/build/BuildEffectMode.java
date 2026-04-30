package dev.grandbuilder.build;

public enum BuildEffectMode {
	STANDARD("standard", false, 0),
	UFO_INVASION("ufo_invasion", true, 54),
	RIFT_BLOOM("rift_bloom", true, 48),
	METEOR_FORGE("meteor_forge", true, 42),
	CLOCKWORK_GRID("clockwork_grid", false, 0),
	AURORA_WEAVE("aurora_weave", false, 0);

	private final String key;
	private final boolean instantReveal;
	private final int revealDelayTicks;

	BuildEffectMode(String key, boolean instantReveal, int revealDelayTicks) {
		this.key = key;
		this.instantReveal = instantReveal;
		this.revealDelayTicks = revealDelayTicks;
	}

	public String translationKey() {
		return "effect.grand_builder." + key;
	}

	public boolean hidesSpeed() {
		return instantReveal;
	}

	public boolean instantReveal() {
		return instantReveal;
	}

	public int revealDelayTicks() {
		return revealDelayTicks;
	}

	public int networkId() {
		return ordinal();
	}

	public BuildEffectMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static BuildEffectMode byNetworkId(int id) {
		BuildEffectMode[] values = values();
		if (id < 0 || id >= values.length) {
			return STANDARD;
		}
		return values[id];
	}
}
