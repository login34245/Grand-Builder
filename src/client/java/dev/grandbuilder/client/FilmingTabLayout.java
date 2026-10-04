package dev.grandbuilder.client;

public record FilmingTabLayout(int x, int y, int width, boolean opensLeft, boolean inHeader) {
	public static final int CLOSED_WIDTH = 18, OPEN_WIDTH = CLOSED_WIDTH * 3, HEIGHT = 20;
	public int height() { return inHeader ? 14 : HEIGHT; }

	public static FilmingTabLayout at(int screenWidth, int panelLeft, int panelTop, int panelWidth, float expansion) {
		int size = Math.round(CLOSED_WIDTH + (OPEN_WIDTH - CLOSED_WIDTH) * Math.clamp(expansion, 0, 1));
		if (panelLeft >= OPEN_WIDTH + 6) return new FilmingTabLayout(panelLeft - size - 3, panelTop + 36, size, true, false);
		if (screenWidth - panelLeft - panelWidth >= OPEN_WIDTH + 6)
			return new FilmingTabLayout(panelLeft + panelWidth + 3, panelTop + 36, size, false, false);
		return new FilmingTabLayout(panelLeft + 6, panelTop + 3, size, false, true);
	}
}
