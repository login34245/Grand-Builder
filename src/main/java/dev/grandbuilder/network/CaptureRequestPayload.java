package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.build.CustomCaptureFormat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CaptureRequestPayload(int formatId) implements CustomPacketPayload {
	public static final Type<CaptureRequestPayload> TYPE = new Type<>(GrandBuilderMod.id("capture_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CaptureRequestPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CaptureRequestPayload::formatId,
		CaptureRequestPayload::new
	);

	public CustomCaptureFormat format() {
		return CustomCaptureFormat.byNetworkId(formatId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
