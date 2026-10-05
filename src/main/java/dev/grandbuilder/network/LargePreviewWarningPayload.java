package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LargePreviewWarningPayload(long token, int kind, String name, int positions, int nonAir, long storageBytes) implements CustomPacketPayload {
	public static final Type<LargePreviewWarningPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "large_preview_warning"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LargePreviewWarningPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, LargePreviewWarningPayload::token, ByteBufCodecs.VAR_INT, LargePreviewWarningPayload::kind,
		ByteBufCodecs.stringUtf8(256), LargePreviewWarningPayload::name, ByteBufCodecs.VAR_INT, LargePreviewWarningPayload::positions,
		ByteBufCodecs.VAR_INT, LargePreviewWarningPayload::nonAir, ByteBufCodecs.VAR_LONG, LargePreviewWarningPayload::storageBytes,
		LargePreviewWarningPayload::new);
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
