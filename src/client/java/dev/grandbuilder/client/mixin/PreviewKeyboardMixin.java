package dev.grandbuilder.client.mixin;

import dev.grandbuilder.GrandBuilderModClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 1100)
public abstract class PreviewKeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void grandBuilder$previewPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (window == client.getWindow().handle() && action == GLFW.GLFW_PRESS && client.screen == null)
			GrandBuilderModClient.recordPreviewPress(event);
	}
}
