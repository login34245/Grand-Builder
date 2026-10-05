package dev.grandbuilder.build;

import dev.grandbuilder.network.LargePreviewWarningPayload;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LargePreviewGuard {
	public static final int BUILD = 0, INSPECT = 1;
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final long LIFETIME = 120_000_000_000L;
	private LargePreviewGuard() {}
	public static boolean needsWarning(int positions, int nonAir) { return positions >= 5_000_000 || nonAir >= 1_000_000; }

	static boolean request(ServerPlayer player, String key, Component name, List<GrandPalaceBlueprint.RelativeBlock> blocks,
		int kind, BooleanSupplier validSelection, Runnable resume) {
		if (!needsWarning(blocks.size(), StructureLibrary.countNonAir(blocks))) return false;
		if (!ServerPlayNetworking.canSend(player, LargePreviewWarningPayload.TYPE)) {
			player.displayClientMessage(Component.translatable("message.grand_builder.large_preview_client"), false);
			return true;
		}
		long token = UUID.randomUUID().getMostSignificantBits();
		var dimension = player.level().dimension();
		WeakReference<List<GrandPalaceBlueprint.RelativeBlock>> reference = new WeakReference<>(blocks);
		PENDING.put(player.getUUID(), new Pending(token, System.nanoTime() + LIFETIME, () -> {
			if (!dimension.equals(player.level().dimension()) || !validSelection.getAsBoolean()) return false;
			var current = StructureLibrary.resolveSelection(key);
			return reference.get() != null && current.key().equals(key)
				&& reference.get() == (current.custom() ? AnimatedBuildManager.capturedBlueprint(player.getUUID()) : current.blueprint());
		}, resume));
		String label = name.getString();
		ServerPlayNetworking.send(player, new LargePreviewWarningPayload(token, kind, label.substring(0, Math.min(256, label.length())),
			blocks.size(), StructureLibrary.countNonAir(blocks), blocks instanceof DenseStructureBlueprint dense ? dense.retainedBytes() : -1));
		return true;
	}
	public static void confirm(ServerPlayer player, long token, boolean accepted) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.token() != token) return;
		PENDING.remove(player.getUUID());
		if (!accepted) return;
		if (System.nanoTime() > pending.expires() || !pending.valid().getAsBoolean()) {
			player.displayClientMessage(Component.translatable("message.grand_builder.large_preview_expired"), false);
			return;
		}
		pending.resume().run();
	}
	public static void expire() { PENDING.values().removeIf(value -> System.nanoTime() > value.expires()); }
	public static void cancel(UUID player) { PENDING.remove(player); }
	public static void clear() { PENDING.clear(); }
	private record Pending(long token, long expires, BooleanSupplier valid, Runnable resume) {}
}
