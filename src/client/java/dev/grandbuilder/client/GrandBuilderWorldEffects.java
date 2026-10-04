package dev.grandbuilder.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.network.StructurePreviewPayload;
import dev.grandbuilder.client.mixin.RenderTypeAccess;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

public final class GrandBuilderWorldEffects {
	private static final RenderStateDataKey<List<GrandBuilderClientEffects.Frame>> SCENES = RenderStateDataKey.create();
	private static final RenderStateDataKey<List<GrandBuilderClientEffects.ActorFrame>> ACTORS = RenderStateDataKey.create();
	private static final RenderStateDataKey<List<GrandBuilderClientEffects.BoltFrame>> BOLTS = RenderStateDataKey.create();
	private static final RenderStateDataKey<List<KineticBlockRenderer.BlockFrame>> BLOCKS = RenderStateDataKey.create();
	private static final RenderStateDataKey<List<StructurePreviewPayload>> PREVIEWS = RenderStateDataKey.create();
	private static final RenderType SOLID = layer("effect_solid", false, true);
	private static final RenderType VEIL = layer("effect_veil", false, false);
	private static final RenderType GLOW = layer("effect_glow", true, false);
	private static final LinkedHashMap<RenderType, ByteBufferBuilder> STORAGE = new LinkedHashMap<>();
	private static final ByteBufferBuilder FALLBACK = new ByteBufferBuilder(256);
	private static MultiBufferSource.BufferSource buffers;

	private GrandBuilderWorldEffects() {
	}

	public static void initialize() {
		STORAGE.put(SOLID, new ByteBufferBuilder(256 * 1024));
		STORAGE.put(VEIL, new ByteBufferBuilder(128 * 1024));
		STORAGE.put(GLOW, new ByteBufferBuilder(512 * 1024));
		STORAGE.put(RenderTypes.solidMovingBlock(), new ByteBufferBuilder(1024 * 1024));
		STORAGE.put(RenderTypes.cutoutMovingBlock(), new ByteBufferBuilder(512 * 1024));
		STORAGE.put(RenderTypes.translucentMovingBlock(), new ByteBufferBuilder(512 * 1024));
		buffers = MultiBufferSource.immediateWithBuffers(STORAGE, FALLBACK);
		WorldRenderEvents.END_EXTRACTION.register(GrandBuilderWorldEffects::extract);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(GrandBuilderWorldEffects::draw);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			STORAGE.values().forEach(ByteBufferBuilder::close);
			FALLBACK.close();
		});
	}

	private static RenderType layer(String name, boolean emissive, boolean writeDepth) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(GrandBuilderMod.id("pipeline/" + name))
			.withVertexShader(GrandBuilderMod.id("core/effect"))
			.withFragmentShader(GrandBuilderMod.id("core/effect"))
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withUniform("Fog", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
			.withCull(false)
			.withDepthWrite(writeDepth)
			.withBlend(emissive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT);
		if (emissive) builder.withShaderDefine("EMISSIVE");
		RenderPipeline pipeline = builder.build();
		registerIris(pipeline);
		return RenderTypeAccess.grandBuilder$create(name, RenderSetup.builder(pipeline)
			.sortOnUpload().bufferSize(256 * 1024).createRenderSetup());
	}

	private static void registerIris(RenderPipeline pipeline) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) return;
		try {
			// POSITION_COLOR needs Iris's basic program; unknown pipelines bypass its world targets.
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			Object instance = api.getMethod("getInstance").invoke(null);
			api.getMethod("assignPipeline", RenderPipeline.class, program)
				.invoke(instance, pipeline, program.getField("BASIC").get(null));
			GrandBuilderMod.LOGGER.info("Registered world effect pipeline {} with Iris", pipeline.getLocation());
		} catch (ReflectiveOperationException | LinkageError exception) {
			GrandBuilderMod.LOGGER.warn("Cannot register world effect pipeline {} with this Iris version",
				pipeline.getLocation(), exception);
		}
	}

	private static void extract(WorldExtractionContext context) {
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		context.worldState().setData(SCENES, GrandBuilderClientEffects.extract(partialTick));
		context.worldState().setData(ACTORS, GrandBuilderClientEffects.extractActors(partialTick));
		context.worldState().setData(BOLTS, GrandBuilderClientEffects.extractBolts(partialTick));
		Vec3 camera = context.worldState().cameraRenderState.pos;
		List<KineticBlockRenderer.BlockFrame> blocks = new java.util.ArrayList<>(KineticBlockRenderer.extract(partialTick, camera));
		blocks.addAll(KineticBlockRenderer.extractPreview(camera));
		context.worldState().setData(BLOCKS, List.copyOf(blocks));
		context.worldState().setData(PREVIEWS, StructurePreviewClientState.visible(camera));
	}

	private static void draw(WorldRenderContext context) {
		List<GrandBuilderClientEffects.Frame> frames = context.worldState().getData(SCENES);
		List<GrandBuilderClientEffects.ActorFrame> actors = context.worldState().getData(ACTORS);
		List<GrandBuilderClientEffects.BoltFrame> bolts = context.worldState().getData(BOLTS);
		List<KineticBlockRenderer.BlockFrame> blocks = context.worldState().getData(BLOCKS);
		List<StructurePreviewPayload> previews = context.worldState().getData(PREVIEWS);
		if (frames == null || actors == null || bolts == null || (frames.isEmpty() && actors.isEmpty() && bolts.isEmpty()
			&& (blocks == null || blocks.isEmpty()) && (previews == null || previews.isEmpty()))) return;
		Vec3 camera = context.worldState().cameraRenderState.pos;
		VertexConsumer solid = buffers.getBuffer(SOLID);
		VertexConsumer veil = buffers.getBuffer(VEIL);
		VertexConsumer glow = buffers.getBuffer(GLOW);
		PoseStack matrices = context.matrices();
		EffectGeometry.Sink sink = (material, x, y, z, color) -> {
			VertexConsumer target = switch (material) { case SOLID -> solid; case VEIL -> veil; case GLOW -> glow; };
			target.addVertex(matrices.last().pose(), x, y, z).setColor(color);
		};
		for (GrandBuilderClientEffects.Frame frame : frames) {
			if (frame.opacity() <= 0) continue;
			if (camera.distanceToSqr(frame.x(), frame.y() + frame.height()*0.5, frame.z()) > 256.0*256.0) continue;
			matrices.pushPose();
			matrices.translate(frame.x()-camera.x, frame.y()-camera.y, frame.z()-camera.z);
			EffectGeometry.emit(frame, sink);
			matrices.popPose();
		}
		for (GrandBuilderClientEffects.ActorFrame actor : actors) {
			if (actor.opacity() <= 0 || camera.distanceToSqr(actor.x(), actor.y() + 1, actor.z()) > 256.0 * 256.0) continue;
			matrices.pushPose();
			matrices.translate(actor.x() - camera.x, actor.y() - camera.y, actor.z() - camera.z);
			EffectGeometry.emitHerobrine(actor, sink);
			matrices.popPose();
		}
		for (GrandBuilderClientEffects.BoltFrame bolt : bolts) {
			if (bolt.opacity() <= 0 || camera.distanceToSqr(bolt.x(),bolt.y(),bolt.z()) > 256.0*256.0) continue;
			matrices.pushPose();
			matrices.translate(bolt.x()-camera.x,bolt.y()-camera.y,bolt.z()-camera.z);
			EffectGeometry.emitLightning(bolt,sink);
			matrices.popPose();
		}
		if (previews != null && !previews.isEmpty()) {
			matrices.pushPose();
			matrices.translate(-camera.x, -camera.y, -camera.z);
			for (StructurePreviewPayload preview : previews) {
				matrices.pushPose();
				if (net.minecraft.client.Minecraft.getInstance().screen instanceof PreviewOrbit.View) {
					double scale = PreviewOrbit.modelScale(preview);
					Vec3 anchor = Vec3.atLowerCornerOf(preview.min());
					matrices.translate(anchor.x, anchor.y, anchor.z);
					matrices.scale((float) scale, (float) scale, (float) scale);
					matrices.translate(-anchor.x, -anchor.y, -anchor.z);
				}
				EffectGeometry.emitPreviewBounds(preview, sink);
				matrices.popPose();
			}
			matrices.popPose();
		}
		buffers.endBatch(SOLID);
		buffers.endBatch(VEIL);
		buffers.endBatch(GLOW);
		if (blocks != null) KineticBlockRenderer.draw(blocks, matrices, camera, buffers);
		buffers.endBatch();
	}
}
