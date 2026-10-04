package dev.grandbuilder.build;

public record BuildOptions(BuildStartSide startSide, DismantleStyle dismantleStyle, boolean destructiveExplosion,
	PlacementPolicy placementPolicy) {
	public static final BuildOptions DEFAULT = new BuildOptions(BuildStartSide.TOP, DismantleStyle.STANDARD, false, false);
	public BuildOptions(BuildStartSide startSide, DismantleStyle dismantleStyle, boolean destructiveExplosion, boolean replaceExistingBlocks) {
		this(startSide, dismantleStyle, destructiveExplosion, replaceExistingBlocks ? PlacementPolicy.REPLACE : PlacementPolicy.PRESERVE);
	}
	public boolean replaceExistingBlocks() { return placementPolicy != PlacementPolicy.PRESERVE; }
	public boolean clearsSite() { return placementPolicy == PlacementPolicy.CLEAR_SITE; }

	public BuildOptions normalized(BuildEffectMode mode) {
		return new BuildOptions(mode == BuildEffectMode.REVERSE ? startSide : BuildStartSide.TOP,
			mode == BuildEffectMode.DISMANTLE ? dismantleStyle : DismantleStyle.STANDARD,
			mode == BuildEffectMode.BUILDER_CHARGE && destructiveExplosion && replaceExistingBlocks(), placementPolicy);
	}

	public boolean canReplace(boolean existingIsAir) {
		return replaceExistingBlocks() || existingIsAir;
	}

	public BuildEffectMode visualMode(BuildEffectMode mode) {
		return mode == BuildEffectMode.DISMANTLE ? dismantleStyle.visualMode() : mode;
	}

	public double effectiveRate(BuildEffectMode mode, BuildSpeed speed) {
		return visualMode(mode).effectiveRate(speed);
	}

	public String displayRate(BuildEffectMode mode, BuildSpeed speed) {
		return visualMode(mode).displayRate(speed);
	}
}
