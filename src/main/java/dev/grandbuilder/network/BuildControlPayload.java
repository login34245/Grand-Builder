package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuildControlPayload(int actionId) implements CustomPacketPayload {
	public static final Type<BuildControlPayload> TYPE = new Type<>(GrandBuilderMod.id("build_control"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildControlPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BuildControlPayload::actionId,
		BuildControlPayload::new
	);

	public BuildControlAction action() {
		return BuildControlAction.byNetworkId(actionId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
