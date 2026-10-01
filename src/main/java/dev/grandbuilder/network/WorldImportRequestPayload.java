package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WorldImportRequestPayload(int action, String sourceId, int candidate, int face, int delta) implements CustomPacketPayload {
	public static final int LIST=0, SCAN=1, SELECT=2, RESIZE=3, SAVE=4, CLOSE=5;
	public static final Type<WorldImportRequestPayload> TYPE = new Type<>(GrandBuilderMod.id("world_import_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldImportRequestPayload> CODEC = StreamCodec.of(
		(buffer, value) -> {
			buffer.writeByte(value.action());
			buffer.writeUtf(value.sourceId(),256);
			buffer.writeVarInt(value.candidate());
			buffer.writeByte(value.face());
			buffer.writeByte(value.delta());
		}, buffer -> new WorldImportRequestPayload(buffer.readUnsignedByte(),buffer.readUtf(256),buffer.readVarInt(),
			buffer.readUnsignedByte(),buffer.readByte()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
