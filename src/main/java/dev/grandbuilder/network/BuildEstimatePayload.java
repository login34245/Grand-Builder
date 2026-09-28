package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildEstimatePayload(int requestId, int totalBlocks, int etaTicks, boolean available) implements CustomPacketPayload {
	public static final Type<BuildEstimatePayload> TYPE = new Type<>(GrandBuilderMod.id("build_estimate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildEstimatePayload> CODEC = StreamCodec.of(
		(b,p) -> { b.writeVarInt(p.requestId()); b.writeVarInt(p.totalBlocks()); b.writeVarInt(p.etaTicks()); b.writeBoolean(p.available()); },
		b -> new BuildEstimatePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
