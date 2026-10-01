package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.build.WorldMapImporter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WorldImportStatePayload(List<SourceEntry> sources, String selectedSource, List<CandidateEntry> candidates,
	int selectedCandidate, WorldMapImporter.Bounds bounds, int status, String savedKey) implements CustomPacketPayload {
	public static final int READY=0, SCANNING=1, LOADING=2, EMPTY=3, ERROR=4, SAVED=5, UNAVAILABLE=6;
	public static final int MAX_SOURCES=64, MAX_CANDIDATES=20;
	public record SourceEntry(String id, String name) { }
	public record CandidateEntry(WorldMapImporter.Bounds bounds, int score) { }
	public WorldImportStatePayload {
		if (sources.size()>MAX_SOURCES || candidates.size()>MAX_CANDIDATES) throw new IllegalArgumentException("Import list overflow");
		sources=List.copyOf(sources); candidates=List.copyOf(candidates);
	}
	public static final Type<WorldImportStatePayload> TYPE = new Type<>(GrandBuilderMod.id("world_import_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldImportStatePayload> CODEC = StreamCodec.of(
		(buffer, value) -> {
			buffer.writeVarInt(value.sources().size());
			for (SourceEntry source : value.sources()) { buffer.writeUtf(source.id(),256); buffer.writeUtf(source.name(),128); }
			buffer.writeUtf(value.selectedSource(),256);
			buffer.writeVarInt(value.candidates().size());
			for (CandidateEntry candidate : value.candidates()) { writeBounds(buffer,candidate.bounds()); buffer.writeVarInt(candidate.score()); }
			buffer.writeVarInt(value.selectedCandidate());
			writeBounds(buffer,value.bounds());
			buffer.writeVarInt(value.status());
			buffer.writeUtf(value.savedKey(),256);
		}, buffer -> {
			int sourceCount=buffer.readVarInt();
			if (sourceCount<0 || sourceCount>MAX_SOURCES) throw new IllegalArgumentException("Invalid import source count");
			List<SourceEntry> sources=new ArrayList<>(sourceCount);
			for (int i=0;i<sourceCount;i++) sources.add(new SourceEntry(buffer.readUtf(256),buffer.readUtf(128)));
			String selected=buffer.readUtf(256);
			int candidateCount=buffer.readVarInt();
			if (candidateCount<0 || candidateCount>MAX_CANDIDATES) throw new IllegalArgumentException("Invalid import candidate count");
			List<CandidateEntry> candidates=new ArrayList<>(candidateCount);
			for (int i=0;i<candidateCount;i++) candidates.add(new CandidateEntry(readBounds(buffer),buffer.readVarInt()));
			return new WorldImportStatePayload(sources,selected,candidates,buffer.readVarInt(),readBounds(buffer),
				buffer.readVarInt(),buffer.readUtf(256));
		});
	private static void writeBounds(RegistryFriendlyByteBuf buffer, WorldMapImporter.Bounds b) {
		buffer.writeInt(b.minX()); buffer.writeInt(b.minY()); buffer.writeInt(b.minZ());
		buffer.writeInt(b.maxX()); buffer.writeInt(b.maxY()); buffer.writeInt(b.maxZ());
	}
	private static WorldMapImporter.Bounds readBounds(RegistryFriendlyByteBuf buffer) {
		return new WorldMapImporter.Bounds(buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readInt());
	}
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
