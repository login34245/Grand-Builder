package dev.grandbuilder.build;

import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.network.StructureListPayload;
import dev.grandbuilder.network.StructurePreviewPayload;
import dev.grandbuilder.network.WorldImportRequestPayload;
import dev.grandbuilder.network.WorldImportStatePayload;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldImportManager {
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "grand-builder-world-import");
		thread.setDaemon(true);
		return thread;
	});
	private WorldImportManager() { }
	private static final class Session {
		List<WorldMapImporter.Source> sources = List.of();
		WorldMapImporter.Source source;
		List<WorldMapImporter.Candidate> candidates = List.of();
		int selected = -1, status = WorldImportStatePayload.READY;
		WorldMapImporter.Bounds crop;
		WorldMapImporter.Loaded loaded;
		BlockPos anchor;
		String savedKey = "";
		boolean visible;
		long generation;
	}
	private static final WorldMapImporter.Bounds EMPTY = new WorldMapImporter.Bounds(0,0,0,0,0,0);

	public static void handle(ServerPlayer player, WorldImportRequestPayload request) {
		if (!player.level().getServer().isSingleplayer()) {
			sendState(player, new Session(), WorldImportStatePayload.UNAVAILABLE);
			return;
		}
		Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
		switch (request.action()) {
			case WorldImportRequestPayload.LIST -> {
				session.visible=true;
				session.sources = WorldMapImporter.sources();
				sendState(player, session, session.status);
				if (session.loaded != null && session.crop != null) sendPreview(player,session);
			}
			case WorldImportRequestPayload.SCAN -> scan(player,session,request.sourceId());
			case WorldImportRequestPayload.SELECT -> select(player,session,request.candidate());
			case WorldImportRequestPayload.RESIZE -> resize(player,session,request.face(),request.delta());
			case WorldImportRequestPayload.SAVE -> save(player,session);
			case WorldImportRequestPayload.CLOSE -> { session.visible=false; clearPreview(player,StructurePreviewPayload.IMPORT); }
			default -> { }
		}
	}
	private static void scan(ServerPlayer player, Session session, String sourceId) {
		WorldMapImporter.Source source = session.sources.stream().filter(entry -> entry.id().equals(sourceId)).findFirst().orElse(null);
		if (source == null) { sendState(player,session,WorldImportStatePayload.ERROR); return; }
		session.source=source; session.candidates=List.of(); session.selected=-1; session.crop=null; session.loaded=null;
		session.savedKey=""; session.anchor=null; session.status=WorldImportStatePayload.SCANNING;
		long generation=++session.generation;
		clearPreview(player,StructurePreviewPayload.IMPORT);
		sendState(player,session,session.status);
		WORKER.submit(() -> {
			List<WorldMapImporter.Candidate> result;
			try { result=WorldMapImporter.scan(source); }
			catch (Exception exception) {
				GrandBuilderMod.LOGGER.warn("Could not scan imported world {}",source.name(),exception);
				result=null;
			}
			List<WorldMapImporter.Candidate> finalResult=result;
			player.level().getServer().execute(() -> {
				if (SESSIONS.get(player.getUUID())!=session || session.generation!=generation) return;
				session.candidates=finalResult==null ? List.of() : finalResult;
				session.status=finalResult==null ? WorldImportStatePayload.ERROR
					: finalResult.isEmpty() ? WorldImportStatePayload.EMPTY : WorldImportStatePayload.READY;
				sendState(player,session,session.status);
				if (!session.candidates.isEmpty()) select(player,session,0);
			});
		});
	}
	private static void select(ServerPlayer player, Session session, int index) {
		if (session.source==null || index<0 || index>=session.candidates.size()) return;
		WorldMapImporter.Candidate candidate=session.candidates.get(index);
		session.selected=index;
		loadCrop(player,session,candidate.bounds());
	}
	private static void loadCrop(ServerPlayer player, Session session, WorldMapImporter.Bounds bounds) {
		WorldMapImporter.Source source=session.source;
		session.crop=bounds; session.loaded=null; session.savedKey="";
		session.status=WorldImportStatePayload.LOADING;
		long generation=++session.generation;
		clearPreview(player,StructurePreviewPayload.IMPORT);
		sendState(player,session,session.status);
		WORKER.submit(() -> {
			WorldMapImporter.Loaded loaded;
			try { loaded=WorldMapImporter.load(source,bounds); }
			catch (Exception exception) {
				GrandBuilderMod.LOGGER.warn("Could not load world crop {}",bounds,exception);
				loaded=null;
			}
			WorldMapImporter.Loaded finalLoaded=loaded;
			player.level().getServer().execute(() -> {
				if (SESSIONS.get(player.getUUID())!=session || session.generation!=generation) return;
				if (finalLoaded==null) { session.status=WorldImportStatePayload.ERROR; sendState(player,session,session.status); return; }
				session.loaded=finalLoaded;
				if (session.anchor==null) {
					int distance=Math.max(12,Math.max(bounds.width(),bounds.depth())/2+6);
					session.anchor=player.blockPosition().relative(player.getDirection(),Math.min(80,distance));
				}
				session.status=WorldImportStatePayload.READY;
				sendPreview(player,session);
				sendState(player,session,session.status);
			});
		});
	}
	private static void resize(ServerPlayer player, Session session, int face, int delta) {
		if (session.loaded==null || session.crop==null || session.status==WorldImportStatePayload.LOADING
			|| face<0 || face>5 || delta==0 || Math.abs(delta)>8) return;
		WorldMapImporter.Bounds adjusted=session.crop.adjust(face,delta);
		if (!adjusted.valid()) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.grand_builder.import_bounds_limit"),true);
			return;
		}
		if (!WorldMapImporter.inside(adjusted,session.loaded.available())) { loadCrop(player,session,adjusted); return; }
		session.crop=adjusted;
		session.status=WorldImportStatePayload.READY;
		session.savedKey="";
		sendPreview(player,session);
		sendState(player,session,session.status);
	}
	private static void save(ServerPlayer player, Session session) {
		if (session.loaded==null || session.crop==null || session.source==null || session.status==WorldImportStatePayload.LOADING) return;
		WorldMapImporter.Loaded loaded=session.loaded;
		WorldMapImporter.Bounds crop=session.crop;
		String name=session.source.name()+"_"+(session.selected+1);
		long generation=++session.generation;
		session.status=WorldImportStatePayload.LOADING;
		sendState(player,session,session.status);
		WORKER.submit(() -> {
			Path path;
			try { path=CustomStructureExporter.exportImported(name,WorldMapImporter.capture(loaded,crop)); }
			catch (Exception exception) {
				GrandBuilderMod.LOGGER.warn("Could not save imported world structure",exception);
				path=null;
			}
			Path finalPath=path;
			player.level().getServer().execute(() -> {
				if (SESSIONS.get(player.getUUID())!=session || session.generation!=generation) return;
				if (finalPath==null) { session.status=WorldImportStatePayload.ERROR; sendState(player,session,session.status); return; }
				StructureLibrary.clearExternalCache();
				session.savedKey="file:"+finalPath.getFileName().toString().replaceFirst("\\.schem$","");
				session.status=WorldImportStatePayload.SAVED;
				ServerPlayNetworking.send(player,new StructureListPayload(StructureLibrary.listNetworkSelections()));
				sendState(player,session,session.status);
			});
		});
	}
	private static void sendPreview(ServerPlayer player, Session session) {
		if (!session.visible || session.crop==null || session.loaded==null || session.anchor==null
			|| !ServerPlayNetworking.canSend(player,StructurePreviewPayload.TYPE)) return;
		WorldMapImporter.Bounds crop=session.crop;
		BlockPos min=session.anchor.offset(-crop.width()/2,0,-crop.depth()/2);
		BlockPos max=min.offset(crop.width()-1,crop.height()-1,crop.depth()-1);
		Map<Long,BlockState> states=new HashMap<>();
		for (Map.Entry<Long,WorldMapImporter.StoredBlock> entry:session.loaded.blocks().entrySet()) {
			BlockPos source=BlockPos.of(entry.getKey());
			if (!crop.contains(source)) continue;
			BlockPos target=min.offset(source.getX()-crop.minX(),source.getY()-crop.minY(),source.getZ()-crop.minZ());
			states.put(target.asLong(),entry.getValue().state());
		}
		ServerPlayNetworking.send(player,StructurePreviewSampler.fromStates(StructurePreviewPayload.IMPORT,
			player.level().dimension().identifier(),min,max,states));
	}
	public static void inspect(ServerPlayer player, String structureKey) {
		if (!ServerPlayNetworking.canSend(player,StructurePreviewPayload.TYPE)) return;
		if (structureKey.isEmpty()) { clearPreview(player,StructurePreviewPayload.INSPECT); return; }
		StructureLibrary.ResolvedStructure structure=StructureLibrary.resolveSelection(structureKey);
		List<GrandPalaceBlueprint.RelativeBlock> blocks=structure.custom()
			? AnimatedBuildManager.capturedBlueprint(player.getUUID()) : structure.blueprint();
		if (!structure.key().equals(structureKey) || blocks==null || blocks.isEmpty()) {
			clearPreview(player,StructurePreviewPayload.INSPECT);
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				structure.custom() ? "message.grand_builder.custom_missing" : "message.grand_builder.structure_load_failed", structure.displayName()),true);
			return;
		}
		int radius=Math.max(12,structure.spawnDistance());
		BlockPos anchor=player.blockPosition().relative(player.getDirection(),Math.min(80,radius));
		ServerPlayNetworking.send(player,StructurePreviewSampler.blueprint(StructurePreviewPayload.INSPECT,
			player.level().dimension().identifier(),anchor,player.getDirection(),blocks));
	}
	private static void sendState(ServerPlayer player, Session session, int status) {
		if (!ServerPlayNetworking.canSend(player,WorldImportStatePayload.TYPE)) return;
		List<WorldImportStatePayload.SourceEntry> sources=new ArrayList<>();
		for (WorldMapImporter.Source source:session.sources) sources.add(new WorldImportStatePayload.SourceEntry(source.id(),
			source.name().length()>128 ? source.name().substring(0,128):source.name()));
		List<WorldImportStatePayload.CandidateEntry> candidates=new ArrayList<>();
		for (WorldMapImporter.Candidate candidate:session.candidates)
			candidates.add(new WorldImportStatePayload.CandidateEntry(candidate.bounds(),candidate.score(),candidate.partial()));
		ServerPlayNetworking.send(player,new WorldImportStatePayload(sources,session.source==null?"":session.source.id(),
			candidates,session.selected,session.crop==null?EMPTY:session.crop,status,session.savedKey));
	}
	private static void clearPreview(ServerPlayer player, int kind) {
		if (!ServerPlayNetworking.canSend(player,StructurePreviewPayload.TYPE)) return;
		ServerPlayNetworking.send(player,new StructurePreviewPayload(kind,false,player.level().dimension().identifier(),
			player.blockPosition(),player.blockPosition(),List.of()));
	}
	public static void onDisconnect(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }
	public static void clear() { SESSIONS.clear(); }
}
