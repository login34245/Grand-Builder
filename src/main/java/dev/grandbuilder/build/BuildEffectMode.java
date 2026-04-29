package dev.grandbuilder.build;

public enum BuildEffectMode {
	STANDARD("standard"),
	UFO_INVASION("ufo_invasion");

	private final String key;

	BuildEffectMode(String key) {
		this.key = key;
	}

	public String translationKey() {
		return "effect.grand_builder." + key;
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
