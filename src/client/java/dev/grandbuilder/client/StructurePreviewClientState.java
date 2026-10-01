package dev.grandbuilder.client;

import dev.grandbuilder.network.StructurePreviewPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class StructurePreviewClientState {
	private static final Map<Integer, StructurePreviewPayload> PREVIEWS = new HashMap<>();
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
	private StructurePreviewClientState() { }
	public static void update(StructurePreviewPayload payload) {
		if (payload.active()) PREVIEWS.put(payload.kind(), payload);
		else PREVIEWS.remove(payload.kind());
		lastLevel=Minecraft.getInstance().level;
	}
	public static void clear() { PREVIEWS.clear(); lastLevel=null; }
	public static StructurePreviewPayload get(int kind) { return PREVIEWS.get(kind); }
	public static void tick(Minecraft client) {
		if (client.level == null || client.player == null) { clear(); return; }
		if (lastLevel!=null && lastLevel!=client.level) clear();
		lastLevel=client.level;
		PREVIEWS.values().removeIf(preview -> !preview.dimension().equals(client.level.dimension().identifier()));
	}
	public static List<StructurePreviewPayload> visible(Vec3 camera) {
		int kind=Minecraft.getInstance().screen instanceof PreviewOrbit.View view ? view.previewKind() : StructurePreviewPayload.BUILD;
		StructurePreviewPayload preview=PREVIEWS.get(kind);
		if (preview==null || !preview.active()) return List.of();
		double distance=camera.distanceToSqr((preview.min().getX()+preview.max().getX()+1)*0.5,
			(preview.min().getY()+preview.max().getY()+1)*0.5,(preview.min().getZ()+preview.max().getZ()+1)*0.5);
		return distance<512*512 ? List.of(preview) : List.of();
	}
}
