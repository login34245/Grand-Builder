package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BuildRequestPayload(String structureKey, int speedId, int effectModeId, int orderId,
	int dismantleStyleId, boolean destructiveExplosion, boolean replaceExistingBlocks) implements CustomPacketPayload {
	public BuildRequestPayload(String structureKey, int speedId, int effectModeId) {
		this(structureKey, speedId, effectModeId, 0, 0, false, false);
	}
	public static final Type<BuildRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "build_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildRequestPayload> CODEC = StreamCodec.of(
		(buffer, payload) -> {
			buffer.writeUtf(payload.structureKey(), 256);
			buffer.writeVarInt(payload.speedId());
			buffer.writeVarInt(payload.effectModeId());
			buffer.writeVarInt(payload.orderId());
			buffer.writeVarInt(payload.dismantleStyleId());
			buffer.writeBoolean(payload.destructiveExplosion());
			buffer.writeBoolean(payload.replaceExistingBlocks());
		},
		buffer -> new BuildRequestPayload(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(),
			buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
