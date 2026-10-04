package dev.grandbuilder.client;

public final class PreviewKeyState {
	private int heldTicks;
	private boolean pendingPress;
	public void press() { pendingPress = true; }
	public boolean tick(boolean active, boolean down, boolean clicked, boolean repeat) {
		clicked |= pendingPress;
		pendingPress = false;
		if (!active) { heldTicks = 0; return false; }
		boolean firstPress = down && heldTicks == 0;
		heldTicks = down ? heldTicks + 1 : 0;
		return clicked || firstPress || repeat && heldTicks > 8 && heldTicks % 4 == 0;
	}
}
