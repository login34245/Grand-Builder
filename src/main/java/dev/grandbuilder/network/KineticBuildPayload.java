package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record KineticBuildPayload(UUID sceneId, Identifier dimension, int cursor, int budget, int delay,
	int cycleAge, int setupRemaining, List<Cell> cells) implements CustomPacketPayload {
	public static final int MAX_CELLS = 3072;
	public record Cell(BlockPos target, int stateId, int index) { }
	public KineticBuildPayload {
		if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("Too many kinetic cells");
		cells = List.copyOf(cells);
	}
	public static final Type<KineticBuildPayload> TYPE = new Type<>(GrandBuilderMod.id("kinetic_build"));
	public static final StreamCodec<RegistryFriendlyByteBuf, KineticBuildPayload> CODEC = StreamCodec.of(
		(buffer, value) -> {
			buffer.writeUUID(value.sceneId());
			buffer.writeIdentifier(value.dimension());
			buffer.writeVarInt(value.cursor());
			buffer.writeVarInt(value.budget());
			buffer.writeVarInt(value.delay());
			buffer.writeVarInt(value.cycleAge());
			buffer.writeVarInt(value.setupRemaining());
			buffer.writeVarInt(value.cells().size());
			for (Cell cell : value.cells()) {
				buffer.writeBlockPos(cell.target());
				buffer.writeVarInt(cell.stateId());
				buffer.writeVarInt(cell.index());
			}
		}, buffer -> {
			UUID scene = buffer.readUUID();
			Identifier dimension = buffer.readIdentifier();
			int cursor = buffer.readVarInt(), budget = buffer.readVarInt(), delay = buffer.readVarInt();
			int cycle = buffer.readVarInt(), setup = buffer.readVarInt(), count = buffer.readVarInt();
			if (count < 0 || count > MAX_CELLS) throw new IllegalArgumentException("Invalid kinetic cell count");
			List<Cell> cells = new ArrayList<>(count);
			for (int i = 0; i < count; i++) cells.add(new Cell(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt()));
			return new KineticBuildPayload(scene, dimension, cursor, budget, delay, cycle, setup, cells);
		});
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
