package dev.grandbuilder.client.mixin;

import dev.grandbuilder.client.GrandBuilderClientEffects;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraEffectsMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "setup", at = @At("TAIL"))
	private void grandBuilder$impact(Level level, Entity entity, boolean detached, boolean mirrored,
		float partialTick, CallbackInfo ci) {
		float power = GrandBuilderClientEffects.shake(partialTick);
		if (power <= 0.0f) {
			return;
		}
		float age = GrandBuilderClientEffects.shakeAge(partialTick);
		Camera camera = (Camera) (Object) this;
		setRotation(camera.yRot() + (float) Math.sin(age * 2.3) * power * 0.65f,
			camera.xRot() + (float) Math.cos(age * 1.7) * power * 0.4f);
		camera.rotation().rotateZ((float) Math.sin(age * 1.4) * power * 0.008f);
	}
}
