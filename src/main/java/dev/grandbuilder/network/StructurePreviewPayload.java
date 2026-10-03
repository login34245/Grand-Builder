package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StructurePreviewPayload(int kind, boolean active, Identifier dimension, BlockPos min, BlockPos max,
	List<Cell> cells) implements CustomPacketPayload {
	public static final int BUILD = 0;
	public static final int IMPORT = 1;
	public static final int INSPECT = 2;
	public static final int MAX_CELLS = 65536;
	public record Cell(BlockPos target, int stateId) { }
	public StructurePreviewPayload {
		if (kind < BUILD || kind > INSPECT || cells.size() > MAX_CELLS) throw new IllegalArgumentException("Invalid preview");
		cells = List.copyOf(cells);
	}
	public static final Type<StructurePreviewPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GrandBuilderMod.MOD_ID, "structure_preview"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StructurePreviewPayload> CODEC = StreamCodec.of(
		(buffer, value) -> {
			buffer.writeByte(value.kind());
			buffer.writeBoolean(value.active());
			buffer.writeIdentifier(value.dimension());
			buffer.writeBlockPos(value.min());
			buffer.writeBlockPos(value.max());
			buffer.writeVarInt(value.cells().size());
			for (Cell cell : value.cells()) {
				buffer.writeBlockPos(cell.target());
				buffer.writeVarInt(cell.stateId());
			}
		}, buffer -> {
			int kind = buffer.readUnsignedByte();
			boolean active = buffer.readBoolean();
			Identifier dimension = buffer.readIdentifier();
			BlockPos min = buffer.readBlockPos(), max = buffer.readBlockPos();
			int count = buffer.readVarInt();
			if (count < 0 || count > MAX_CELLS) throw new IllegalArgumentException("Invalid preview cell count");
			List<Cell> cells = new ArrayList<>(count);
			for (int i = 0; i < count; i++) cells.add(new Cell(buffer.readBlockPos(), buffer.readVarInt()));
			return new StructurePreviewPayload(kind, active, dimension, min, max, cells);
		});
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
