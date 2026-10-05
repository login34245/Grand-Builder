package dev.grandbuilder.client.mixin;

import dev.grandbuilder.client.BuilderTipPreferences;
import dev.grandbuilder.client.BuilderTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class BuilderButtonThemeMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void grandBuilder$lightButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen == null || !client.screen.getClass().getName().startsWith("dev.grandbuilder.client.")
			|| BuilderTipPreferences.get().theme() != BuilderTheme.LIGHT) return;
		AbstractButton button = (AbstractButton) (Object) this;
		if (button instanceof net.minecraft.client.gui.components.Checkbox checkbox) {
			int size = net.minecraft.client.gui.components.Checkbox.getBoxSize(client.font);
			String sprite = "widget/checkbox" + (checkbox.selected() ? "_selected" : "")
				+ (checkbox.isHoveredOrFocused() ? "_highlighted" : "");
			graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				net.minecraft.resources.Identifier.withDefaultNamespace(sprite), button.getX(), button.getY(), size, size, 0xFFFFFFFF);
			var lines = client.font.split(button.getMessage(), Math.max(1, button.getWidth() - size - 4));
			int y = button.getY() + (size - lines.size() * client.font.lineHeight) / 2;
			for (var line : lines) {
				graphics.drawString(client.font, line, button.getX() + size + 4, y, button.active ? 0xFF222B30 : 0xFF6A747B, false);
				y += client.font.lineHeight;
			}
			ci.cancel();
			return;
		}
		if (!button.getClass().getName().startsWith("net.minecraft.client.gui.components.")
			|| !(button instanceof net.minecraft.client.gui.components.Button
				|| button instanceof net.minecraft.client.gui.components.CycleButton)) return;
		boolean focus = button.active && button.isHoveredOrFocused();
		int x = button.getX(), y = button.getY(), right = button.getRight(), bottom = button.getBottom();
		graphics.fill(x, y, right, bottom, focus ? 0xFF486B64 : 0xFF929BA2);
		graphics.fillGradient(x + 1, y + 1, right - 1, bottom - 1,
			!button.active ? 0xFFD9DDE0 : focus ? 0xFFD2E2DC : 0xFFF9FAFB,
			!button.active ? 0xFFD9DDE0 : focus ? 0xFFC1D5CD : 0xFFE0E5E8);
		graphics.drawString(client.font, button.getMessage(), x + (button.getWidth() - client.font.width(button.getMessage())) / 2,
			y + (button.getHeight() - 8) / 2, button.active ? 0xFF222B30 : 0xFF6A747B, false);
		ci.cancel();
	}
}
