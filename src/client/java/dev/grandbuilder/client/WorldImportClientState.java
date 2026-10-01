package dev.grandbuilder.client;

import dev.grandbuilder.build.WorldMapImporter;
import dev.grandbuilder.network.WorldImportStatePayload;
import java.util.List;

public final class WorldImportClientState {
	private static WorldImportStatePayload state = new WorldImportStatePayload(List.of(),"",List.of(),-1,
		new WorldMapImporter.Bounds(0,0,0,0,0,0),WorldImportStatePayload.READY,"");
	private static int revision;
	private WorldImportClientState() { }
	public static void update(WorldImportStatePayload payload) { state=payload; revision++; }
	public static WorldImportStatePayload snapshot() { return state; }
	public static int revision() { return revision; }
	public static void clear() {
		state=new WorldImportStatePayload(List.of(),"",List.of(),-1,
			new WorldMapImporter.Bounds(0,0,0,0,0,0),WorldImportStatePayload.READY,"");
		revision++;
	}
}
