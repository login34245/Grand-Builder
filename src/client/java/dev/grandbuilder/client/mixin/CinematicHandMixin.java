package dev.grandbuilder.client.mixin;

import dev.grandbuilder.client.CinematicCamera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class CinematicHandMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void grandBuilder$cinematicHand(float partialTick, PoseStack pose, SubmitNodeCollector collector,
		LocalPlayer player, int light, CallbackInfo ci) {
		if (CinematicCamera.current(partialTick) != null) ci.cancel();
	}
}
