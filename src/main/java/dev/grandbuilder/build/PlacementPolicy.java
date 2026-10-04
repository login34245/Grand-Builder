package dev.grandbuilder.build;

public enum PlacementPolicy {
	PRESERVE("blocks_keep"), REPLACE("blocks_replace"), CLEAR_SITE("blocks_clear");
	private final String key;
	PlacementPolicy(String key) { this.key = key; }
	public String translationKey() { return "screen.grand_builder." + key; }
	public String tooltipKey() { return translationKey() + "_tooltip"; }
	public static PlacementPolicy byId(int id) {
		return id >= 0 && id < values().length ? values()[id] : PRESERVE;
	}
}
