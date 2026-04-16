package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildSetSpeedPayload(int speedId) implements CustomPacketPayload {
	public static final Type<BuildSetSpeedPayload> TYPE = new Type<>(GrandBuilderMod.id("set_speed"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildSetSpeedPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BuildSetSpeedPayload::speedId,
		BuildSetSpeedPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
