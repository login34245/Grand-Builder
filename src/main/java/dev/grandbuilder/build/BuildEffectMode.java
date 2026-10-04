package dev.grandbuilder.build;

public enum BuildEffectMode {
	STANDARD("standard", false, 0),
	UFO_INVASION("ufo_invasion", true, 54),
	RIFT_BLOOM("rift_bloom", true, 48),
	METEOR_FORGE("meteor_forge", true, 42),
	CLOCKWORK_GRID("clockwork_grid", false, 0),
	AURORA_WEAVE("aurora_weave", false, 0),
	HEROBRINE("herobrine", false, 0),
	BUILDER_CHARGE("builder_charge", true, 72),
	LIGHTNING("lightning", false, 0),
	REVERSE("reverse", false, 0),
	DISMANTLE("dismantle", false, 0),
	FLYING_BLOCKS("flying_blocks", false, 0),
	REVERSE_COLLAPSE("reverse_collapse", false, 0),
	ASSEMBLY_WORKSHOP("assembly_workshop", false, 0),
	SCALE_MODEL("scale_model", false, 0),
	ORBITAL_STRIKE("orbital_strike", true, 92);

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

	public boolean kinetic() {
		return this == FLYING_BLOCKS || this == REVERSE_COLLAPSE || this == ASSEMBLY_WORKSHOP || this == SCALE_MODEL;
	}

	public int setupTicks() {
		return switch (this) {
			case FLYING_BLOCKS -> 12;
			case REVERSE_COLLAPSE, ASSEMBLY_WORKSHOP -> 32;
			case SCALE_MODEL -> 48;
			default -> 0;
		};
	}

	public double effectiveRate(BuildSpeed speed) {
		return speed.effectiveBlocksPerTick();
	}

	public String displayRate(BuildSpeed speed) {
		return speed.displayRate();
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
