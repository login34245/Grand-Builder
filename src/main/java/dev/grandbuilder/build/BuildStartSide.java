package dev.grandbuilder.build;

import java.util.Comparator;

public enum BuildStartSide {
	TOP, FRONT, RIGHT, LEFT, BACK;

	public String translationKey() { return "order.grand_builder." + name().toLowerCase(java.util.Locale.ROOT); }
	public BuildStartSide next() { return values()[(ordinal() + 1) % values().length]; }
	public static BuildStartSide byId(int id) { return id >= 0 && id < values().length ? values()[id] : TOP; }

	public Comparator<GrandPalaceBlueprint.RelativeBlock> comparator() {
		Comparator<GrandPalaceBlueprint.RelativeBlock> primary = switch (this) {
			case TOP -> Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::y).reversed();
			case FRONT -> Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::z);
			case BACK -> Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::z).reversed();
			case LEFT -> Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::x);
			case RIGHT -> Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::x).reversed();
		};
		return primary.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::y)
			.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::x).thenComparingInt(GrandPalaceBlueprint.RelativeBlock::z);
	}
}
