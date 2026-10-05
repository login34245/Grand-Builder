package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LargePreviewConfirmPayload(long token, boolean accepted) implements CustomPacketPayload {
	public static final Type<LargePreviewConfirmPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "large_preview_confirm"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LargePreviewConfirmPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, LargePreviewConfirmPayload::token, ByteBufCodecs.BOOL, LargePreviewConfirmPayload::accepted, LargePreviewConfirmPayload::new);
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
