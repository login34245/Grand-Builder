package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FilmingToolsPayload(int actionId) implements CustomPacketPayload {
	public static final Type<FilmingToolsPayload> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "filming_tools"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FilmingToolsPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, FilmingToolsPayload::actionId, FilmingToolsPayload::new);
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
