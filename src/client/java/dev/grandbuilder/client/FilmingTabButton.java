package dev.grandbuilder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class FilmingTabButton extends Button {
	private static final ItemStack ICON = new ItemStack(Items.SPYGLASS);
	private final java.util.function.Supplier<FilmingTabLayout> layout;
	private float expansion;
	private long lastFrame = System.nanoTime();

	FilmingTabButton(java.util.function.Supplier<FilmingTabLayout> layout, OnPress action) {
		super(0, 0, FilmingTabLayout.CLOSED_WIDTH, FilmingTabLayout.HEIGHT,
			Component.translatable("screen.grand_builder.filming.title"), action, DEFAULT_NARRATION);
		this.layout = layout;
		setTooltip(Tooltip.create(getMessage()));
		position();
	}

	float expansion() { return expansion; }

	private FilmingTabLayout position() {
		FilmingTabLayout bounds = layout.get();
		setX(bounds.x()); setY(bounds.y()); setWidth(bounds.width()); setHeight(bounds.height());
		return bounds;
	}

	@Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		double elapsed = Math.clamp((now - lastFrame) / 1_000_000.0, 0, 100);
		lastFrame = now;
		float target = isHoveredOrFocused() ? 1 : 0;
		expansion += (target - expansion) * (float) (1 - Math.exp(-elapsed / 40));
		if (Math.abs(target - expansion) < 0.01f) expansion = target;
		FilmingTabLayout bounds = position();
		graphics.fill(getX(), getY(), getRight(), getBottom(), isHoveredOrFocused() ? 0xFF729BB8 : 0xFF456783);
		graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, 0xFF172B3C);
		int iconX = bounds.opensLeft() ? getRight() - 17 : getX() + 1;
		if (bounds.inHeader()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(iconX + 2, getY() + 1);
			graphics.pose().scale(0.75f);
			graphics.renderItem(ICON, 0, 0);
			graphics.pose().popMatrix();
		} else graphics.renderItem(ICON, iconX, getY() + 2);
		if (getWidth() > 25) {
			int textLeft = bounds.opensLeft() ? getX() + 3 : getX() + 20;
			int textRight = bounds.opensLeft() ? iconX - 1 : getRight() - 2;
			graphics.enableScissor(textLeft, getY(), textRight, getBottom());
			graphics.drawString(Minecraft.getInstance().font, Component.translatable("screen.grand_builder.filming.tab"),
				textLeft, getY() + (bounds.inHeader() ? 3 : 6), 0xFFFFDEA3);
			graphics.disableScissor();
		}
	}
}
