package dev.grandbuilder.client.mixin;

import dev.grandbuilder.client.CinematicCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CinematicHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void grandBuilder$cinematicHud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		float partial = delta.getGameTimeDeltaPartialTick(false);
		if (CinematicCamera.current(partial) == null) return;
		CinematicCamera.renderOverlay(graphics, partial);
		ci.cancel();
	}
}
