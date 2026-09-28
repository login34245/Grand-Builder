package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LightningStrikePayload(UUID sceneId, Identifier dimension, int sequence, BlockPos target,
	int duration, int age, boolean dismantling) implements CustomPacketPayload {
	public static final Type<LightningStrikePayload> TYPE = new Type<>(GrandBuilderMod.id("lightning_strike"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LightningStrikePayload> CODEC = StreamCodec.of(
		(b,p) -> { b.writeUUID(p.sceneId()); b.writeIdentifier(p.dimension()); b.writeVarInt(p.sequence());
			b.writeBlockPos(p.target()); b.writeVarInt(p.duration()); b.writeVarInt(p.age()); b.writeBoolean(p.dismantling()); },
		b -> new LightningStrikePayload(b.readUUID(),b.readIdentifier(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
