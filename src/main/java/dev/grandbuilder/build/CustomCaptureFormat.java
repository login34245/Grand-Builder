package dev.grandbuilder.build;

public enum CustomCaptureFormat {
	RUNTIME("runtime"),
	NBT_SINGLE("nbt_single"),
	NBT_PIECES("nbt_pieces"),
	SCHEM_SINGLE("schem_single");

	private final String key;

	CustomCaptureFormat(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}

	public String translationKey() {
		return "capture_format.grand_builder." + key;
	}

	public boolean writesFiles() {
		return this != RUNTIME;
	}

	public int networkId() {
		return ordinal();
	}

	public CustomCaptureFormat next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static CustomCaptureFormat byNetworkId(int id) {
		CustomCaptureFormat[] values = values();
		if (id < 0 || id >= values.length) {
			return SCHEM_SINGLE;
		}
		return values[id];
	}
}
