package dev.grandbuilder.client;

public record FilmingTabLayout(int x, int y, int width, boolean opensLeft, boolean inHeader) {
	public static final int CLOSED_WIDTH = 18, OPEN_WIDTH = CLOSED_WIDTH * 3, HEIGHT = 20;
	public int height() { return inHeader ? 14 : HEIGHT; }

	public static FilmingTabLayout at(int screenWidth, int panelLeft, int panelTop, int panelWidth, float expansion) {
		return at(screenWidth, panelLeft, panelTop, panelWidth, expansion, 0);
	}
	public static FilmingTabLayout at(int screenWidth, int panelLeft, int panelTop, int panelWidth, float expansion, int slot) {
		int size = Math.round(CLOSED_WIDTH + (OPEN_WIDTH - CLOSED_WIDTH) * Math.clamp(expansion, 0, 1));
		int sideY = panelTop + 36 + slot * (HEIGHT + 4);
		if (panelLeft >= OPEN_WIDTH + 6) return new FilmingTabLayout(panelLeft - size - 3, sideY, size, true, false);
		if (screenWidth - panelLeft - panelWidth >= OPEN_WIDTH + 6)
			return new FilmingTabLayout(panelLeft + panelWidth + 3, sideY, size, false, false);
		return new FilmingTabLayout(panelLeft + 6 + slot * (OPEN_WIDTH + 3), panelTop + 3, size, false, true);
	}
}
