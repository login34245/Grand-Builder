package dev.grandbuilder.client;

public enum BuilderTheme {
	CURRENT(0xCC0E1A2D, 0xCC081018, 0xF0142234, 0xF01B314A, 0xFF577EA3, 0xFFF2F7FF, 0xFFB9D8F6, 0xFFFFDEA3),
	DARK(0xCC111214, 0xCC08090B, 0xF0202226, 0xF0282B30, 0xFF737A84, 0xFFF0F2F5, 0xFFB9C1CC, 0xFFF2CE83),
	LIGHT(0x9924282C, 0xB0171A1D, 0xFFF0F1F3, 0xFFE5E7EA, 0xFF89929B, 0xFF22282D, 0xFF48545E, 0xFF65501D);

	final int backdropTop, backdropBottom, panelTop, panelBottom, border, text, muted, accent;
	BuilderTheme(int backdropTop, int backdropBottom, int panelTop, int panelBottom, int border, int text, int muted, int accent) {
		this.backdropTop = backdropTop; this.backdropBottom = backdropBottom;
		this.panelTop = panelTop; this.panelBottom = panelBottom; this.border = border;
		this.text = text; this.muted = muted; this.accent = accent;
	}
	public String translationKey() { return "screen.grand_builder.settings.theme." + name().toLowerCase(java.util.Locale.ROOT); }
	public boolean textShadow() { return this != LIGHT; }
	static BuilderTheme current() { return BuilderTipPreferences.get().theme(); }
}
