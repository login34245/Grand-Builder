package dev.grandbuilder.client;

public final class PreviewConfirmState {
	private static boolean awaitingConfirm;

	private PreviewConfirmState() {
	}

	public static boolean isAwaitingConfirm() {
		return awaitingConfirm;
	}

	public static void arm() {
		awaitingConfirm = true;
	}

	public static void disarm() {
		awaitingConfirm = false;
	}
}
