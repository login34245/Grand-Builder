package dev.grandbuilder;

import com.mojang.blaze3d.platform.InputConstants;
import dev.grandbuilder.client.BuilderMenuScreen;
import dev.grandbuilder.client.FilmingToolsScreen;
import dev.grandbuilder.network.FilmingStatePayload;
import dev.grandbuilder.client.BuildStatusClientState;
import dev.grandbuilder.client.GrandBuilderClientEffects;
import dev.grandbuilder.client.GrandBuilderWorldEffects;
import dev.grandbuilder.client.PreviewConfirmState;
import dev.grandbuilder.client.PreviewKeyState;
import dev.grandbuilder.client.StructureListClientState;
import dev.grandbuilder.client.StructurePreviewClientState;
import dev.grandbuilder.client.WorldImportClientState;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.network.BuildEffectPayload;
import dev.grandbuilder.network.BuildEstimatePayload;
import dev.grandbuilder.network.LightningStrikePayload;
import dev.grandbuilder.network.HerobrinePlacementPayload;
import dev.grandbuilder.network.KineticBuildPayload;
import dev.grandbuilder.network.BuildStatusPayload;
import dev.grandbuilder.network.StructureListPayload;
import dev.grandbuilder.network.StructurePreviewPayload;
import dev.grandbuilder.network.WorldImportStatePayload;
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
	private static final KeyMapping FAST_PREVIEW_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
		"key.grand_builder.fast_preview_movement", InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_LEFT_SHIFT, GRAND_BUILDER_CATEGORY
	));

	private static boolean hintShown;
	private static final KeyMapping CINEMATIC_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
		"key.grand_builder.skip_cinematic", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, GRAND_BUILDER_CATEGORY));
	public static Component cinematicKeyName() { return CINEMATIC_KEY.getTranslatedKeyMessage(); }
	private static final PreviewKey[] PREVIEW_KEYS = {
		new PreviewKey("rotate_preview", GLFW.GLFW_KEY_R, BuildControlAction.ROTATE_PREVIEW, false),
		new PreviewKey("move_preview_forward", GLFW.GLFW_KEY_UP, BuildControlAction.MOVE_PREVIEW_FORWARD, true),
		new PreviewKey("move_preview_back", GLFW.GLFW_KEY_DOWN, BuildControlAction.MOVE_PREVIEW_BACK, true),
		new PreviewKey("move_preview_left", GLFW.GLFW_KEY_LEFT, BuildControlAction.MOVE_PREVIEW_LEFT, true),
		new PreviewKey("move_preview_right", GLFW.GLFW_KEY_RIGHT, BuildControlAction.MOVE_PREVIEW_RIGHT, true),
		new PreviewKey("move_preview_up", GLFW.GLFW_KEY_PAGE_UP, BuildControlAction.MOVE_PREVIEW_UP, true),
		new PreviewKey("move_preview_down", GLFW.GLFW_KEY_PAGE_DOWN, BuildControlAction.MOVE_PREVIEW_DOWN, true)
	};

	private static final class PreviewKey {
		private final KeyMapping key;
		private final BuildControlAction action;
		private final boolean repeat;
		private final PreviewKeyState state = new PreviewKeyState();
		private PreviewKey(String name, int code, BuildControlAction action, boolean repeat) {
			this.key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.grand_builder." + name,
				InputConstants.Type.KEYSYM, code, GRAND_BUILDER_CATEGORY));
			this.action = action;
			this.repeat = repeat;
		}
		private void tick(boolean active) {
			boolean clicked = false;
			while (key.consumeClick()) clicked = true;
			if (state.tick(active, key.isDown(), clicked, repeat))
				ClientPlayNetworking.send(new BuildControlPayload(action.networkId(), repeat && FAST_PREVIEW_KEY.isDown()));
		}
	}

	public static Component fastPreviewKeyName() { return FAST_PREVIEW_KEY.getTranslatedKeyMessage(); }
	public static void recordPreviewPress(net.minecraft.client.input.KeyEvent event) {
		for (PreviewKey binding : PREVIEW_KEYS) if (binding.key.matches(event)) binding.state.press();
	}
	public static Component previewKeyName(BuildControlAction action) {
		for (PreviewKey binding : PREVIEW_KEYS) if (binding.action == action) return binding.key.getTranslatedKeyMessage();
		return Component.empty();
	}

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(dev.grandbuilder.network.LargePreviewWarningPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				Minecraft client = context.client();
				net.minecraft.client.gui.screens.Screen previous = client.screen;
				String positions = String.format(java.util.Locale.US, "%,d", payload.positions());
				String solid = String.format(java.util.Locale.US, "%,d", payload.nonAir());
				Component text = Component.translatable("screen.grand_builder.large_preview_text", payload.name(), positions, solid);
				if (payload.storageBytes() >= 0) text = text.copy().append(Component.translatable("screen.grand_builder.large_preview_memory",
					String.format(java.util.Locale.US, "%.1f", payload.storageBytes() / 1048576.0)));
				client.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
					ClientPlayNetworking.send(new dev.grandbuilder.network.LargePreviewConfirmPayload(payload.token(), accepted));
					if (accepted) client.setScreen(previous);
					else {
						PreviewConfirmState.disarm();
						if (payload.kind() == dev.grandbuilder.build.LargePreviewGuard.INSPECT)
							ClientPlayNetworking.send(new dev.grandbuilder.network.StructureInspectRequestPayload(""));
						client.setScreen(new BuilderMenuScreen());
					}
				}, Component.translatable("screen.grand_builder.large_preview_title"), text,
					Component.translatable("screen.grand_builder.large_preview_continue"), Component.translatable("gui.cancel")));
			}));
		ClientPlayNetworking.registerGlobalReceiver(BuildStatusPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				BuildStatusClientState.update(payload);
				if (payload.modeId() == 2) PreviewConfirmState.arm();
				else PreviewConfirmState.disarm();
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(FilmingStatePayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				if (context.client().screen instanceof FilmingToolsScreen screen) screen.receiveState(payload);
			}));
		ClientPlayNetworking.registerGlobalReceiver(StructureListPayload.TYPE, (payload, context) ->
			context.client().execute(() -> StructureListClientState.update(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(StructurePreviewPayload.TYPE, (payload, context) ->
			context.client().execute(() -> StructurePreviewClientState.update(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(WorldImportStatePayload.TYPE, (payload, context) ->
			context.client().execute(() -> WorldImportClientState.update(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(BuildEffectPayload.TYPE, (payload, context) ->
			context.client().execute(() -> GrandBuilderClientEffects.trigger(payload))
		);
		HudRenderCallback.EVENT.register(GrandBuilderClientEffects::render);
		ClientPlayNetworking.registerGlobalReceiver(BuildEstimatePayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				if (context.client().screen instanceof BuilderMenuScreen menu) menu.receiveEstimate(payload);
			}));
		ClientPlayNetworking.registerGlobalReceiver(LightningStrikePayload.TYPE, (payload, context) ->
			context.client().execute(() -> GrandBuilderClientEffects.strike(payload)));
		ClientPlayNetworking.registerGlobalReceiver(HerobrinePlacementPayload.TYPE, (payload, context) ->
			context.client().execute(() -> GrandBuilderClientEffects.place(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(KineticBuildPayload.TYPE, (payload, context) ->
			context.client().execute(() -> GrandBuilderClientEffects.kinetic(payload)));
		GrandBuilderWorldEffects.initialize();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GrandBuilderClientEffects.tick(client);
			StructurePreviewClientState.tick(client);
			while (FAST_PREVIEW_KEY.consumeClick()) { }
			while (CINEMATIC_KEY.consumeClick()) if (client.player != null && client.screen == null)
				GrandBuilderClientEffects.dismissCinematic(client.player.getUUID());
			if (client.player == null) {
				for (PreviewKey key : PREVIEW_KEYS) key.tick(false);
				PreviewConfirmState.disarm();
				WorldImportClientState.clear();
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
			for (PreviewKey key : PREVIEW_KEYS) key.tick(holdingCore && client.screen == null && PreviewConfirmState.isAwaitingConfirm());

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
				if (client.screen != null || !PreviewConfirmState.isAwaitingConfirm()) {
					continue;
				}
				PreviewConfirmState.disarm();
				ClientPlayNetworking.send(new BuildControlPayload(BuildControlAction.CONFIRM_PREVIEW.networkId()));
			}

			while (CANCEL_PREVIEW_KEY.consumeClick()) {
				if (client.screen != null || !PreviewConfirmState.isAwaitingConfirm()) {
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
