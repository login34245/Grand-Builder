package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BuildDefaultsPayload(int speedId, boolean terrainEnabled) implements CustomPacketPayload {
	public static final Type<BuildDefaultsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "build_defaults"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildDefaultsPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BuildDefaultsPayload::speedId, ByteBufCodecs.BOOL, BuildDefaultsPayload::terrainEnabled, BuildDefaultsPayload::new);
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
