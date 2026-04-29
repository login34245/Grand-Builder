package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildEffectPayload(int effectModeId, int phaseId, int durationTicks, float intensity) implements CustomPacketPayload {
	public static final int PHASE_ARRIVAL = 0;
	public static final int PHASE_REVEAL = 1;

	public static final Type<BuildEffectPayload> TYPE = new Type<>(GrandBuilderMod.id("build_effect"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildEffectPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BuildEffectPayload::effectModeId,
		ByteBufCodecs.VAR_INT, BuildEffectPayload::phaseId,
		ByteBufCodecs.VAR_INT, BuildEffectPayload::durationTicks,
		ByteBufCodecs.FLOAT, BuildEffectPayload::intensity,
		BuildEffectPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
