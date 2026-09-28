package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildEstimateRequestPayload(int requestId, BuildRequestPayload selection) implements CustomPacketPayload {
	public static final Type<BuildEstimateRequestPayload> TYPE = new Type<>(GrandBuilderMod.id("build_estimate_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildEstimateRequestPayload> CODEC = StreamCodec.of(
		(b,p) -> { b.writeVarInt(p.requestId()); BuildRequestPayload.CODEC.encode(b,p.selection()); },
		b -> new BuildEstimateRequestPayload(b.readVarInt(),BuildRequestPayload.CODEC.decode(b)));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
