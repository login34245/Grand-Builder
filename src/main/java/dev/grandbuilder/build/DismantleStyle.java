package dev.grandbuilder.build;

public enum DismantleStyle {
	STANDARD(BuildEffectMode.STANDARD), HEROBRINE(BuildEffectMode.HEROBRINE),
	LIGHTNING(BuildEffectMode.LIGHTNING), CHARGE(BuildEffectMode.BUILDER_CHARGE);

	private final BuildEffectMode visualMode;
	DismantleStyle(BuildEffectMode visualMode) { this.visualMode = visualMode; }
	public BuildEffectMode visualMode() { return visualMode; }
	public String translationKey() { return "dismantle.grand_builder." + name().toLowerCase(java.util.Locale.ROOT); }
	public DismantleStyle next() { return values()[(ordinal() + 1) % values().length]; }
	public static DismantleStyle byId(int id) { return id >= 0 && id < values().length ? values()[id] : STANDARD; }
}
