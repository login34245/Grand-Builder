package dev.grandbuilder.build;

import java.util.Locale;

public enum BuildEffectMode {
	STANDARD("standard", false, 0),
	UFO_INVASION("ufo_invasion", true, 54),
	RIFT_BLOOM("rift_bloom", true, 48),
	METEOR_FORGE("meteor_forge", true, 42),
	CLOCKWORK_GRID("clockwork_grid", false, 0),
	AURORA_WEAVE("aurora_weave", false, 0),
	HEROBRINE("herobrine", false, 0),
	BUILDER_CHARGE("builder_charge", true, 72);

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
		return instantReveal || this == CLOCKWORK_GRID;
	}

	public boolean instantReveal() {
		return instantReveal;
	}

	public double effectiveRate(BuildSpeed speed) {
		return this == HEROBRINE ? 1.0 / HerobrineTiming.cycleTicks(speed.effectiveBlocksPerTick())
			: speed.effectiveBlocksPerTick();
	}

	public String displayRate(BuildSpeed speed) {
		return this == HEROBRINE ? String.format(Locale.US, "%.2f", effectiveRate(speed)) : speed.displayRate();
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
