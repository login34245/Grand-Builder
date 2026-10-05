package dev.grandbuilder;

import dev.grandbuilder.build.AnimatedBuildManager;
import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.BuildOptions;
import dev.grandbuilder.build.BuildStartSide;
import dev.grandbuilder.build.DismantleStyle;
import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.CustomCaptureFormat;
import dev.grandbuilder.build.StructureSelectionManager;
import dev.grandbuilder.build.StructureLibrary;
import dev.grandbuilder.build.WorldImportManager;
import dev.grandbuilder.build.FilmingToolsManager;
import dev.grandbuilder.network.FilmingToolsPayload;
import dev.grandbuilder.network.FilmingStatePayload;
import dev.grandbuilder.config.GrandBuilderConfig;
import dev.grandbuilder.item.StructureCoreItem;
import dev.grandbuilder.item.StructureSelectorItem;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.network.BuildEffectPayload;
import dev.grandbuilder.network.BuildEstimatePayload;
import dev.grandbuilder.network.BuildEstimateRequestPayload;
import dev.grandbuilder.network.LightningStrikePayload;
import dev.grandbuilder.network.HerobrinePlacementPayload;
import dev.grandbuilder.network.KineticBuildPayload;
import dev.grandbuilder.network.BuildRequestPayload;
import dev.grandbuilder.network.BuildSetSpeedPayload;
import dev.grandbuilder.network.BuildStatusPayload;
import dev.grandbuilder.network.CaptureRequestPayload;
import dev.grandbuilder.network.StructureListPayload;
import dev.grandbuilder.network.StructurePreviewPayload;
import dev.grandbuilder.network.StructureInspectRequestPayload;
import dev.grandbuilder.network.WorldImportRequestPayload;
import dev.grandbuilder.network.WorldImportStatePayload;
import static net.minecraft.commands.Commands.literal;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrandBuilderMod implements ModInitializer {
	public static final String MOD_ID = "grand_builder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier STRUCTURE_CORE_ID = id("structure_core");
	public static final Identifier STRUCTURE_SELECTOR_ID = id("structure_selector");
	public static final Identifier CLOCK_TICK_ID = id("effect.clock_tick");
	public static final ResourceKey<Item> STRUCTURE_CORE_KEY = ResourceKey.create(Registries.ITEM, STRUCTURE_CORE_ID);
	public static final ResourceKey<Item> STRUCTURE_SELECTOR_KEY = ResourceKey.create(Registries.ITEM, STRUCTURE_SELECTOR_ID);
	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("tab"));
	public static final SoundEvent CLOCK_TICK = SoundEvent.createVariableRangeEvent(CLOCK_TICK_ID);
	public static final SoundEvent BLOCK_FLIGHT = SoundEvent.createVariableRangeEvent(id("effect.block_flight"));
	public static final SoundEvent RUBBLE_PULL = SoundEvent.createVariableRangeEvent(id("effect.rubble_pull"));
	public static final SoundEvent ASSEMBLY_SERVO = SoundEvent.createVariableRangeEvent(id("effect.assembly_servo"));
	public static final SoundEvent MODEL_UNFOLD = SoundEvent.createVariableRangeEvent(id("effect.model_unfold"));

	public static final Item STRUCTURE_CORE = new StructureCoreItem(new Item.Properties()
		.setId(STRUCTURE_CORE_KEY)
		.stacksTo(1)
		.rarity(Rarity.EPIC));
	public static final Item STRUCTURE_SELECTOR = new StructureSelectorItem(new Item.Properties()
		.setId(STRUCTURE_SELECTOR_KEY)
		.stacksTo(1)
		.rarity(Rarity.RARE));

	@Override
	public void onInitialize() {
		GrandBuilderConfig.load();

		Registry.register(BuiltInRegistries.ITEM, STRUCTURE_CORE_ID, STRUCTURE_CORE);
		Registry.register(BuiltInRegistries.ITEM, STRUCTURE_SELECTOR_ID, STRUCTURE_SELECTOR);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CLOCK_TICK_ID, CLOCK_TICK);
		Registry.register(BuiltInRegistries.SOUND_EVENT, BLOCK_FLIGHT.location(), BLOCK_FLIGHT);
		Registry.register(BuiltInRegistries.SOUND_EVENT, RUBBLE_PULL.location(), RUBBLE_PULL);
		Registry.register(BuiltInRegistries.SOUND_EVENT, ASSEMBLY_SERVO.location(), ASSEMBLY_SERVO);
		Registry.register(BuiltInRegistries.SOUND_EVENT, MODEL_UNFOLD.location(), MODEL_UNFOLD);
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_KEY, FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.grand_builder.main"))
			.icon(() -> new ItemStack(STRUCTURE_CORE))
			.displayItems((parameters, output) -> {
				output.accept(STRUCTURE_CORE);
				output.accept(STRUCTURE_SELECTOR);
			})
			.build());

		StructureLibrary.ensureStructuresDirectory();

		PayloadTypeRegistry.playC2S().register(BuildRequestPayload.TYPE, BuildRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BuildEstimateRequestPayload.TYPE, BuildEstimateRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BuildEstimatePayload.TYPE, BuildEstimatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LightningStrikePayload.TYPE, LightningStrikePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BuildControlPayload.TYPE, BuildControlPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BuildSetSpeedPayload.TYPE, BuildSetSpeedPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CaptureRequestPayload.TYPE, CaptureRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BuildEffectPayload.TYPE, BuildEffectPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HerobrinePlacementPayload.TYPE, HerobrinePlacementPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(KineticBuildPayload.TYPE, KineticBuildPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BuildStatusPayload.TYPE, BuildStatusPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StructureListPayload.TYPE, StructureListPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StructurePreviewPayload.TYPE, StructurePreviewPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StructureInspectRequestPayload.TYPE, StructureInspectRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WorldImportRequestPayload.TYPE, WorldImportRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WorldImportStatePayload.TYPE, WorldImportStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FilmingToolsPayload.TYPE, FilmingToolsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FilmingStatePayload.TYPE, FilmingStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.grandbuilder.network.LargePreviewWarningPayload.TYPE, dev.grandbuilder.network.LargePreviewWarningPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.grandbuilder.network.LargePreviewConfirmPayload.TYPE, dev.grandbuilder.network.LargePreviewConfirmPayload.CODEC);

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!player.getItemInHand(hand).is(STRUCTURE_SELECTOR)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (world.isClientSide()) {
				// Let the selector click reach the server, but stop normal block damage on the client.
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				StructureSelectionManager.setFirst(serverPlayer, pos);
			}
			return net.minecraft.world.InteractionResult.FAIL;
		});

		ServerPlayNetworking.registerGlobalReceiver(BuildRequestPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			AnimatedBuildManager.setSelection(
				context.player().getUUID(),
				payload.structureKey(),
				BuildSpeed.byNetworkId(payload.speedId()),
				BuildEffectMode.byNetworkId(payload.effectModeId())
			);
			AnimatedBuildManager.setOptions(context.player().getUUID(), new BuildOptions(BuildStartSide.byId(payload.orderId()),
				DismantleStyle.byId(payload.dismantleStyleId()), payload.destructiveExplosion(), payload.placementPolicy()));
			AnimatedBuildManager.preparePreview(context.player());
		}));
		ServerPlayNetworking.registerGlobalReceiver(BuildEstimateRequestPayload.TYPE, (payload, context) ->
			context.server().execute(() -> AnimatedBuildManager.sendEstimate(context.player(), payload)));

		ServerPlayNetworking.registerGlobalReceiver(BuildControlPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			BuildControlAction action = payload.action();
			switch (action) {
				case STATUS -> {
					sendStructureList(context.player());
					AnimatedBuildManager.sendBuildStatus(context.player(), true);
				}
				case STATUS_SILENT -> AnimatedBuildManager.sendBuildStatus(context.player(), false);
				case CONFIRM_PREVIEW -> AnimatedBuildManager.confirmPreview(context.player());
				case CANCEL_PREVIEW -> AnimatedBuildManager.cancelPreview(context.player());
				case TOGGLE_PAUSE -> AnimatedBuildManager.togglePause(context.player());
				case SPEED_UP -> AnimatedBuildManager.adjustSpeed(context.player(), 1);
				case SPEED_DOWN -> AnimatedBuildManager.adjustSpeed(context.player(), -1);
				case ROLLBACK -> AnimatedBuildManager.rollbackLastBuild(context.player());
				case CAPTURE_CUSTOM -> {
					AnimatedBuildManager.captureCustomStructure(context.player(), CustomCaptureFormat.RUNTIME);
					sendStructureList(context.player());
				}
				case TOGGLE_TERRAIN -> AnimatedBuildManager.toggleTerrainAdaptation(context.player());
				case REQUEST_STRUCTURE_LIST -> sendStructureList(context.player());
				case ROTATE_PREVIEW, MOVE_PREVIEW_FORWARD, MOVE_PREVIEW_BACK, MOVE_PREVIEW_LEFT,
					MOVE_PREVIEW_RIGHT, MOVE_PREVIEW_UP, MOVE_PREVIEW_DOWN -> AnimatedBuildManager.adjustPreview(context.player(), action, payload.fastMovement());
			}
		}));

		ServerPlayNetworking.registerGlobalReceiver(BuildSetSpeedPayload.TYPE, (payload, context) -> context.server().execute(() ->
			AnimatedBuildManager.setSpeed(context.player(), BuildSpeed.byNetworkId(payload.speedId()), false)
		));
		ServerPlayNetworking.registerGlobalReceiver(CaptureRequestPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			AnimatedBuildManager.captureCustomStructure(context.player(), payload.format());
			sendStructureList(context.player());
		}));
		ServerPlayNetworking.registerGlobalReceiver(StructureInspectRequestPayload.TYPE, (payload, context) ->
			context.server().execute(() -> WorldImportManager.inspect(context.player(), payload.structureKey())));
		ServerPlayNetworking.registerGlobalReceiver(dev.grandbuilder.network.LargePreviewConfirmPayload.TYPE, (payload, context) ->
			context.server().execute(() -> dev.grandbuilder.build.LargePreviewGuard.confirm(context.player(), payload.token(), payload.accepted())));
		ServerPlayNetworking.registerGlobalReceiver(WorldImportRequestPayload.TYPE, (payload, context) ->
			context.server().execute(() -> WorldImportManager.handle(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(FilmingToolsPayload.TYPE, (payload, context) ->
			context.server().execute(() -> FilmingToolsManager.handle(context.player(), payload)));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			dev.grandbuilder.build.LargePreviewGuard.expire();
			AnimatedBuildManager.tick(server);
			StructureSelectionManager.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
			AnimatedBuildManager.onPlayerDisconnect(handler.player);
			WorldImportManager.onDisconnect(handler.player);
			FilmingToolsManager.disconnect(handler.player.getUUID());
			dev.grandbuilder.build.LargePreviewGuard.cancel(handler.player.getUUID());
		}));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			AnimatedBuildManager.shutdown();
			WorldImportManager.clear();
			FilmingToolsManager.clear();
			dev.grandbuilder.build.LargePreviewGuard.clear();
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("grandbuilder")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(literal("reload").executes(context -> {
					GrandBuilderConfig.reload();
					StructureLibrary.clearExternalCache();
					context.getSource().sendSuccess(() -> Component.translatable("command.grand_builder.reload_ok"), true);
					return 1;
				}))
				.then(literal("status").executes(context -> {
					GrandBuilderConfig config = GrandBuilderConfig.get();
					context.getSource().sendSuccess(() -> Component.translatable(
						"command.grand_builder.status",
						config.maxBlocksPerBuild,
						config.maxBuildRadius,
						config.permissionMode
					), false);
					return 1;
				}))
			)
		);

		LOGGER.info("Grand Builder initialized. Config: {}", GrandBuilderConfig.configPath());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static void sendStructureList(ServerPlayer player) {
		ServerPlayNetworking.send(player, new StructureListPayload(StructureLibrary.listNetworkSelections()));
	}
}
