package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildStatusPayload(
	int modeId,
	String structureName,
	float progressPercent,
	int remainingBlocks,
	int etaTicks,
	boolean paused,
	int speedId,
	float speedBlocksPerTick,
	boolean terrainAdaptationEnabled
) implements CustomPacketPayload {
	public static final Type<BuildStatusPayload> TYPE = new Type<>(GrandBuilderMod.id("build_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildStatusPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BuildStatusPayload::modeId,
		ByteBufCodecs.stringUtf8(256), BuildStatusPayload::structureName,
		ByteBufCodecs.FLOAT, BuildStatusPayload::progressPercent,
		ByteBufCodecs.VAR_INT, BuildStatusPayload::remainingBlocks,
		ByteBufCodecs.VAR_INT, BuildStatusPayload::etaTicks,
		ByteBufCodecs.BOOL, BuildStatusPayload::paused,
		ByteBufCodecs.VAR_INT, BuildStatusPayload::speedId,
		ByteBufCodecs.FLOAT, BuildStatusPayload::speedBlocksPerTick,
		ByteBufCodecs.BOOL, BuildStatusPayload::terrainAdaptationEnabled,
		BuildStatusPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
