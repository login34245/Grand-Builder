package dev.grandbuilder.build;

import java.util.List;
import java.util.Locale;

public enum BuildStructure {
	CUSTOM("structure.grand_builder.custom", 10, true);

	private final String translationKey;
	private final int spawnDistance;
	private final boolean custom;

	BuildStructure(
		String translationKey,
		int spawnDistance,
		boolean custom
	) {
		this.translationKey = translationKey;
		this.spawnDistance = spawnDistance;
		this.custom = custom;
	}

	public String translationKey() {
		return translationKey;
	}

	public int spawnDistance() {
		return spawnDistance;
	}

	public List<GrandPalaceBlueprint.RelativeBlock> createBlueprint() {
		return List.of();
	}

	public boolean isCustom() {
		return custom;
	}

	public int networkId() {
		return ordinal();
	}

	public String selectionKey() {
		return "builtin:" + name().toLowerCase(Locale.ROOT);
	}

	public BuildStructure next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static BuildStructure byNetworkId(int id) {
		BuildStructure[] values = values();
		if (id < 0 || id >= values.length) {
			return CUSTOM;
		}
		return values[id];
	}

	public static BuildStructure bySelectionKey(String key) {
		if (key == null || !key.startsWith("builtin:")) {
			return null;
		}

		String token = key.substring("builtin:".length()).toUpperCase(Locale.ROOT);
		try {
			return BuildStructure.valueOf(token);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
