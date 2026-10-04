package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FilmingStatePayload(boolean permitted, boolean invisible, int dayTime, int weatherId, int result) implements CustomPacketPayload {
	public static final int STATUS = 0, APPLIED = 1, DENIED = 2, COOLDOWN = 3;
	public static final Type<FilmingStatePayload> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "filming_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FilmingStatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, FilmingStatePayload::permitted, ByteBufCodecs.BOOL, FilmingStatePayload::invisible,
		ByteBufCodecs.VAR_INT, FilmingStatePayload::dayTime, ByteBufCodecs.VAR_INT, FilmingStatePayload::weatherId,
		ByteBufCodecs.VAR_INT, FilmingStatePayload::result, FilmingStatePayload::new);
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
