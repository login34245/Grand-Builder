package dev.grandbuilder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.grandbuilder.network.KineticBuildPayload;
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
	public record BlockFrame(double x, double y, double z, KineticGeometry.Pose pose,
		List<BakedQuad> quads, RenderType layer, int tint) { }
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
				BlockStateModel model = client.getBlockRenderer().getBlockModel(state);
				ModelKey key = new ModelKey(cell.stateId(), cell.index() & 7);
				CachedModel cached = MODELS.get(key);
				if (cached == null || cached.model() != model) {
					List<BakedQuad> quads = new ArrayList<>();
					for (var part : model.collectParts(RandomSource.create(key.variant()))) {
						quads.addAll(part.getQuads(null));
						for (Direction face : Direction.values()) quads.addAll(part.getQuads(face));
					}
					cached = new CachedModel(model, List.copyOf(quads));
					if (MODELS.size() >= 512) MODELS.remove(MODELS.keySet().iterator().next());
					MODELS.put(key, cached);
				}
				int tint = client.getBlockColors().getColor(state, client.level, cell.target(), 0);
				result.add(new BlockFrame(f.x(), f.y(), f.z(), pose, cached.quads(), ItemBlockRenderTypes.getMovingBlockRenderType(state), tint));
			}
		}
		return List.copyOf(result);
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
					((color >> 8) & 255) / 255f * shade, (color & 255) / 255f * shade, 1,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			}
			matrices.popPose();
		}
	}
}
