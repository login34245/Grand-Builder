package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BuildControlPayload(int actionId, boolean fastMovement) implements CustomPacketPayload {
	public static final Type<BuildControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "build_control"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildControlPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BuildControlPayload::actionId,
		ByteBufCodecs.BOOL, BuildControlPayload::fastMovement,
		BuildControlPayload::new
	);

	public BuildControlPayload(int actionId) {
		this(actionId, false);
	}

	public BuildControlAction action() {
		return BuildControlAction.byNetworkId(actionId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
