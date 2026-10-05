package dev.grandbuilder.client;

public record BuilderSettingsLayout(int left, int top, int width, int height, int step, int rowHeight) {
	public static BuilderSettingsLayout at(int screenWidth, int screenHeight) {
		int width = Math.min(360, screenWidth - 16), height = Math.min(278, screenHeight - 16);
		int step = (height - 60) / 7;
		return new BuilderSettingsLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
			width, height, step, Math.min(20, step - 3));
	}
	public int rowY(int row) { return top + 30 + row * step; }
	public int footerY() { return top + height - 30; }
}
