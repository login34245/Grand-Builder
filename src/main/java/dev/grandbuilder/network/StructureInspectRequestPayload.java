package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StructureInspectRequestPayload(String structureKey) implements CustomPacketPayload {
	public static final Type<StructureInspectRequestPayload> TYPE = new Type<>(GrandBuilderMod.id("structure_inspect_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StructureInspectRequestPayload> CODEC = StreamCodec.of(
		(buffer,value) -> buffer.writeUtf(value.structureKey(),256), buffer -> new StructureInspectRequestPayload(buffer.readUtf(256)));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
