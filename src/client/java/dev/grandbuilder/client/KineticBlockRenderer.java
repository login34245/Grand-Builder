package dev.grandbuilder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.grandbuilder.network.KineticBuildPayload;
import dev.grandbuilder.network.StructurePreviewPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class KineticBlockRenderer {
	private record ModelKey(int stateId, int variant) { }
	private record CachedModel(BlockStateModel model, List<BakedQuad> quads) { }
	private static final Map<ModelKey, CachedModel> MODELS = new LinkedHashMap<>();
	private record CachedPreview(StructurePreviewPayload payload, boolean inspection, Object level,
		BlockStateModel resourceModel, List<BlockFrame> frames) { }
	private static CachedPreview cachedPreview;
	public record BlockFrame(double x, double y, double z, KineticGeometry.Pose pose,
		List<BakedQuad> quads, RenderType layer, int tint, float alpha) { }
	private KineticBlockRenderer() { }

	public static List<BlockFrame> extract(float partialTick, Vec3 camera) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return List.of();
		List<BlockFrame> result = new ArrayList<>();
		for (var frame : GrandBuilderClientEffects.extractKinetic(partialTick)) {
			var f = frame.scene();
			if (camera.distanceToSqr(f.x(), f.y() + f.height() * 0.5, f.z()) > 256 * 256) continue;
			for (KineticBuildPayload.Cell cell : frame.cells()) {
				if (cell.index() < frame.cursor()) continue;
				KineticGeometry.Pose pose = KineticGeometry.pose(frame, cell);
				if (pose == null || result.size() >= 4096) continue;
				BlockState state = Block.stateById(cell.stateId());
				if (state.getRenderShape() != RenderShape.MODEL) continue;
				CachedModel cached = model(client, state, cell.stateId(), cell.index() & 7);
				int tint = client.getBlockColors().getColor(state, client.level, cell.target(), 0);
				result.add(new BlockFrame(f.x(), f.y(), f.z(), pose, cached.quads(), ItemBlockRenderTypes.getMovingBlockRenderType(state), tint, 1));
			}
		}
		return List.copyOf(result);
	}
	public static List<BlockFrame> extractPreview(Vec3 camera) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) { cachedPreview = null; return List.of(); }
		List<BlockFrame> result = new ArrayList<>();
		for (StructurePreviewPayload preview : StructurePreviewClientState.visible(camera)) {
			boolean inspection = client.screen instanceof PreviewOrbit.View;
			BlockStateModel resourceModel = client.getBlockRenderer().getBlockModel(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			if (cachedPreview != null && cachedPreview.payload() == preview && cachedPreview.inspection() == inspection
				&& cachedPreview.level() == client.level && cachedPreview.resourceModel() == resourceModel) return cachedPreview.frames();
			double scale = inspection ? PreviewOrbit.modelScale(preview) : 1;
			java.util.Set<Long> opaque = new java.util.HashSet<>();
			for (var cell : preview.cells()) if (Block.stateById(cell.stateId()).isSolidRender()) opaque.add(cell.target().asLong());
			for (StructurePreviewPayload.Cell cell : preview.cells()) {
				if (result.size() >= StructurePreviewPayload.MAX_CELLS) break;
				BlockState state = Block.stateById(cell.stateId());
				if (state.getRenderShape() != RenderShape.MODEL) continue;
				CachedModel cached = model(client, state, cell.stateId(), cell.target().hashCode() & 7);
				if (cached.quads().isEmpty()) continue;
				List<BakedQuad> quads = cached.quads();
				if (state.isSolidRender()) quads = quads.stream()
					.filter(quad -> !opaque.contains(cell.target().relative(quad.direction()).asLong())).toList();
				if (quads.isEmpty()) continue;
				int tint = client.getBlockColors().getColor(state, client.level, cell.target(), 0);
				Vec3 position = PreviewOrbit.modelPosition(preview, Vec3.atCenterOf(cell.target()), scale);
				KineticGeometry.Pose pose = new KineticGeometry.Pose(position.x, position.y, position.z, 0, 0, 0, scale);
				// Use recognized moving-block pipelines so Iris can supply the matching shader program.
				result.add(new BlockFrame(0, 0, 0, pose, quads, inspection
					? ItemBlockRenderTypes.getMovingBlockRenderType(state) : RenderTypes.translucentMovingBlock(), tint, inspection ? 1 : 0.64f));
			}
			cachedPreview = new CachedPreview(preview, inspection, client.level, resourceModel, List.copyOf(result));
			return cachedPreview.frames();
		}
		cachedPreview = null;
		return List.copyOf(result);
	}
	private static CachedModel model(Minecraft client, BlockState state, int stateId, int variant) {
		BlockStateModel blockModel = client.getBlockRenderer().getBlockModel(state);
		ModelKey key = new ModelKey(stateId, variant);
		CachedModel cached = MODELS.get(key);
		if (cached == null || cached.model() != blockModel) {
			List<BakedQuad> quads = new ArrayList<>();
			for (var part : blockModel.collectParts(RandomSource.create(variant))) {
				quads.addAll(part.getQuads(null));
				for (Direction face : Direction.values()) quads.addAll(part.getQuads(face));
			}
			cached = new CachedModel(blockModel, List.copyOf(quads));
			if (MODELS.size() >= 512) MODELS.remove(MODELS.keySet().iterator().next());
			MODELS.put(key, cached);
		}
		return cached;
	}

	public static void draw(List<BlockFrame> frames, PoseStack matrices, Vec3 camera, MultiBufferSource buffers) {
		for (BlockFrame frame : frames) {
			var p = frame.pose();
			matrices.pushPose();
			matrices.translate(frame.x() + p.x() - camera.x, frame.y() + p.y() - camera.y, frame.z() + p.z() - camera.z);
			matrices.mulPose(new Quaternionf().rotationXYZ((float) p.pitch(), (float) p.yaw(), (float) p.roll()));
			matrices.scale((float) p.scale(), (float) p.scale(), (float) p.scale());
			matrices.translate(-0.5, -0.5, -0.5);
			var consumer = buffers.getBuffer(frame.layer());
			for (BakedQuad quad : frame.quads()) {
				int color = quad.isTinted() ? frame.tint() : 0xFFFFFF;
				float shade = quad.shade() ? switch (quad.direction()) {
					case DOWN -> 0.5f;
					case NORTH, SOUTH -> 0.8f;
					case WEST, EAST -> 0.65f;
					default -> 1;
				} : 1;
				consumer.putBulkData(matrices.last(), quad, ((color >> 16) & 255) / 255f * shade,
					((color >> 8) & 255) / 255f * shade, (color & 255) / 255f * shade, frame.alpha(),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			}
			matrices.popPose();
		}
	}
}
