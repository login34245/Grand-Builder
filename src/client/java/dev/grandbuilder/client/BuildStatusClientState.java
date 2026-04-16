package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.network.BuildStatusPayload;

public final class BuildStatusClientState {
	private static volatile Snapshot snapshot = Snapshot.empty();

	private BuildStatusClientState() {
	}

	public static void update(BuildStatusPayload payload) {
		snapshot = new Snapshot(
			payload.modeId(),
			payload.structureName(),
			payload.progressPercent(),
			payload.remainingBlocks(),
			payload.etaTicks(),
			payload.paused(),
			payload.speedId(),
			payload.speedBlocksPerTick(),
			payload.terrainAdaptationEnabled()
		);
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static void reset() {
		snapshot = Snapshot.empty();
	}

	public record Snapshot(
		int modeId,
		String structureName,
		float progressPercent,
		int remainingBlocks,
		int etaTicks,
		boolean paused,
		int speedId,
		float speedBlocksPerTick,
		boolean terrainAdaptationEnabled
	) {
		public static Snapshot empty() {
			return new Snapshot(0, "", 0.0f, 0, 0, false, BuildSpeed.NORMAL.networkId(), 0.0f, true);
		}
	}
}
