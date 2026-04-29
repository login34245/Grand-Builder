package dev.grandbuilder;

import com.mojang.blaze3d.platform.InputConstants;
import dev.grandbuilder.client.BuilderMenuScreen;
import dev.grandbuilder.client.BuildStatusClientState;
import dev.grandbuilder.client.GrandBuilderClientEffects;
import dev.grandbuilder.client.PreviewConfirmState;
import dev.grandbuilder.client.StructureListClientState;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.network.BuildEffectPayload;
import dev.grandbuilder.network.BuildStatusPayload;
import dev.grandbuilder.network.StructureListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

public class GrandBuilderModClient implements ClientModInitializer {
	private static final KeyMapping.Category GRAND_BUILDER_CATEGORY = KeyMapping.Category.register(GrandBuilderMod.id("keybinds"));

	private static final KeyMapping OPEN_CONSOLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
		"key.grand_builder.open_console",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_B,
		GRAND_BUILDER_CATEGORY
	));
	private static final KeyMapping CONFIRM_PREVIEW_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
		"key.grand_builder.confirm_preview",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_ENTER,
		GRAND_BUILDER_CATEGORY
	));
	private static final KeyMapping CANCEL_PREVIEW_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
		"key.grand_builder.cancel_preview",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_X,
		GRAND_BUILDER_CATEGORY
	));

	private static boolean hintShown;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(BuildStatusPayload.TYPE, (payload, context) ->
			context.client().execute(() -> BuildStatusClientState.update(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(StructureListPayload.TYPE, (payload, context) ->
			context.client().execute(() -> StructureListClientState.update(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(BuildEffectPayload.TYPE, (payload, context) ->
			context.client().execute(() -> GrandBuilderClientEffects.trigger(payload))
		);
		HudRenderCallback.EVENT.register(GrandBuilderClientEffects::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GrandBuilderClientEffects.tick(client);
			if (client.player == null) {
				PreviewConfirmState.disarm();
				return;
			}

			boolean holdingCore = client.player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_CORE)
				|| client.player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_CORE);
			boolean holdingConsoleTool = holdingCore
				|| client.player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_SELECTOR)
				|| client.player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_SELECTOR);
			if (!holdingCore) {
				PreviewConfirmState.disarm();
			}

			if (!hintShown && holdingConsoleTool && client.screen == null) {
				client.player.displayClientMessage(Component.translatable("message.grand_builder.client_hint"), true);
				hintShown = true;
			}

			while (OPEN_CONSOLE_KEY.consumeClick()) {
				if (!holdingConsoleTool) {
					continue;
				}
				if (client.screen != null) {
					continue;
				}
				ClientPlayNetworking.send(new BuildControlPayload(BuildControlAction.STATUS.networkId()));
				client.setScreen(new BuilderMenuScreen());
			}

			while (CONFIRM_PREVIEW_KEY.consumeClick()) {
				if (!PreviewConfirmState.isAwaitingConfirm()) {
					continue;
				}
				PreviewConfirmState.disarm();
				ClientPlayNetworking.send(new BuildControlPayload(BuildControlAction.CONFIRM_PREVIEW.networkId()));
			}

			while (CANCEL_PREVIEW_KEY.consumeClick()) {
				if (!PreviewConfirmState.isAwaitingConfirm()) {
					continue;
				}
				PreviewConfirmState.disarm();
				ClientPlayNetworking.send(new BuildControlPayload(BuildControlAction.CANCEL_PREVIEW.networkId()));
			}
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClientSide()) {
				return InteractionResult.PASS;
			}

			if (player.getItemInHand(hand).getItem() != GrandBuilderMod.STRUCTURE_CORE) {
				return InteractionResult.PASS;
			}

			if (PreviewConfirmState.isAwaitingConfirm()) {
				PreviewConfirmState.disarm();
				ClientPlayNetworking.send(new BuildControlPayload(BuildControlAction.CONFIRM_PREVIEW.networkId()));
				return InteractionResult.FAIL;
			}

			ClientPlayNetworking.send(new BuildControlPayload(BuildControlAction.STATUS.networkId()));
			Minecraft.getInstance().setScreen(new BuilderMenuScreen());
			return InteractionResult.FAIL;
		});
	}
}
