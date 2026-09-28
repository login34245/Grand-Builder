package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BuildEffectPayload(
	UUID sceneId, Identifier dimension, BlockPos min, BlockPos max,
	int effectModeId, int phaseId, int durationTicks, int ageTicks, float progress, float intensity,
	boolean dismantling, int orderId, boolean destructive
) implements CustomPacketPayload {
	public BuildEffectPayload(UUID sceneId, Identifier dimension, BlockPos min, BlockPos max,
		int effectModeId, int phaseId, int durationTicks, int ageTicks, float progress, float intensity) {
		this(sceneId,dimension,min,max,effectModeId,phaseId,durationTicks,ageTicks,progress,intensity,false,0,false);
	}
	public static final int PHASE_ARRIVAL = 0;
	public static final int PHASE_REVEAL = 1;
	public static final int PHASE_BUILD = 2;
	public static final int PHASE_PAUSED = 3;
	public static final int PHASE_STOP = 4;

	public static final Type<BuildEffectPayload> TYPE = new Type<>(GrandBuilderMod.id("build_effect"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildEffectPayload> CODEC = StreamCodec.of(
		(buffer, value) -> {
			buffer.writeUUID(value.sceneId());
			buffer.writeIdentifier(value.dimension());
			buffer.writeBlockPos(value.min());
			buffer.writeBlockPos(value.max());
			buffer.writeVarInt(value.effectModeId());
			buffer.writeVarInt(value.phaseId());
			buffer.writeVarInt(value.durationTicks());
			buffer.writeVarInt(value.ageTicks());
			buffer.writeFloat(value.progress());
			buffer.writeFloat(value.intensity());
			buffer.writeBoolean(value.dismantling());
			buffer.writeVarInt(value.orderId());
			buffer.writeBoolean(value.destructive());
		},
		buffer -> new BuildEffectPayload(
			buffer.readUUID(), buffer.readIdentifier(), buffer.readBlockPos(), buffer.readBlockPos(),
			buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
			buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean()
		)
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
