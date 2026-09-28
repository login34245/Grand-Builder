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
	REQUEST_STRUCTURE_LIST,
	ROTATE_PREVIEW,
	MOVE_PREVIEW_FORWARD,
	MOVE_PREVIEW_BACK,
	MOVE_PREVIEW_LEFT,
	MOVE_PREVIEW_RIGHT,
	MOVE_PREVIEW_UP,
	MOVE_PREVIEW_DOWN;

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
