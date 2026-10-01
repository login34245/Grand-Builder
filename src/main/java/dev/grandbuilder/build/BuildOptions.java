package dev.grandbuilder.build;

public record BuildOptions(BuildStartSide startSide, DismantleStyle dismantleStyle, boolean destructiveExplosion,
	boolean replaceExistingBlocks) {
	public static final BuildOptions DEFAULT = new BuildOptions(BuildStartSide.TOP, DismantleStyle.STANDARD, false, false);

	public BuildOptions normalized(BuildEffectMode mode) {
		return new BuildOptions(mode == BuildEffectMode.REVERSE ? startSide : BuildStartSide.TOP,
			mode == BuildEffectMode.DISMANTLE ? dismantleStyle : DismantleStyle.STANDARD,
			mode == BuildEffectMode.BUILDER_CHARGE && destructiveExplosion && replaceExistingBlocks, replaceExistingBlocks);
	}

	public boolean canReplace(boolean existingIsAir) {
		return replaceExistingBlocks || existingIsAir;
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
