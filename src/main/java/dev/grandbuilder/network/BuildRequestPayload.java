package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildRequestPayload(String structureKey, int speedId) implements CustomPacketPayload {
	public static final Type<BuildRequestPayload> TYPE = new Type<>(GrandBuilderMod.id("build_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildRequestPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(256), BuildRequestPayload::structureKey,
		ByteBufCodecs.VAR_INT, BuildRequestPayload::speedId,
		BuildRequestPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
