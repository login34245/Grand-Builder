package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HerobrinePlacementPayload(UUID sceneId, Identifier dimension, int sequence, BlockPos target,
	double x, double y, double z, int blockStateId, int duration, int age) implements CustomPacketPayload {
	public static final Type<HerobrinePlacementPayload> TYPE = new Type<>(GrandBuilderMod.id("herobrine_placement"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrinePlacementPayload> CODEC = StreamCodec.of(
		(buffer, value) -> {
			buffer.writeUUID(value.sceneId());
			buffer.writeIdentifier(value.dimension());
			buffer.writeVarInt(value.sequence());
			buffer.writeBlockPos(value.target());
			buffer.writeDouble(value.x());
			buffer.writeDouble(value.y());
			buffer.writeDouble(value.z());
			buffer.writeVarInt(value.blockStateId());
			buffer.writeVarInt(value.duration());
			buffer.writeVarInt(value.age());
		},
		buffer -> new HerobrinePlacementPayload(buffer.readUUID(), buffer.readIdentifier(), buffer.readVarInt(),
			buffer.readBlockPos(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
			buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
