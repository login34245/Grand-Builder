package dev.grandbuilder.network;

public enum BuildControlAction {
	STATUS,
	STATUS_SILENT,
	CONFIRM_PREVIEW,
	CANCEL_PREVIEW,
	TOGGLE_PAUSE,
	SPEED_UP,
	SPEED_DOWN,
	ROLLBACK,
	CAPTURE_CUSTOM,
	TOGGLE_TERRAIN,
	REQUEST_STRUCTURE_LIST;

	public int networkId() {
		return ordinal();
	}

	public static BuildControlAction byNetworkId(int id) {
		BuildControlAction[] values = values();
		if (id < 0 || id >= values.length) {
			return STATUS;
		}
		return values[id];
	}
}
