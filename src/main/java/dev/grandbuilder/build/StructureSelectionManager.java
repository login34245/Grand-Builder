package dev.grandbuilder.build;

import dev.grandbuilder.GrandBuilderMod;
import dev.grandbuilder.config.GrandBuilderConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public final class StructureSelectionManager {
	private static final Map<UUID, MutableSelection> SELECTIONS = new HashMap<>();
	private static final int PARTICLE_INTERVAL_TICKS = 6;
	private static final int MAX_EDGE_SAMPLES = 72;

	private StructureSelectionManager() {
	}

	public static void setFirst(ServerPlayer player, BlockPos pos) {
		setCorner(player, pos, true);
	}

	public static void setSecond(ServerPlayer player, BlockPos pos) {
		setCorner(player, pos, false);
	}

	public static CompleteSelection completeSelection(ServerPlayer player) {
		MutableSelection selection = SELECTIONS.get(player.getUUID());
		if (selection == null || selection.first == null || selection.second == null) {
			return null;
		}
		if (!selection.dimension.equals(player.level().dimension())) {
			player.displayClientMessage(Component.translatable("message.grand_builder.selection_wrong_dimension"), true);
			return null;
		}
		return CompleteSelection.from(selection.dimension, selection.first, selection.second);
	}

	public static boolean hasAnySelection(ServerPlayer player) {
		return SELECTIONS.containsKey(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		if ((server.getTickCount() % PARTICLE_INTERVAL_TICKS) != 0 || SELECTIONS.isEmpty()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isHoldingSelector(player)) {
				continue;
			}

			MutableSelection selection = SELECTIONS.get(player.getUUID());
			if (selection == null || !selection.dimension.equals(player.level().dimension())) {
				continue;
			}

			if (selection.first != null && selection.second != null) {
				drawSelectionBox(player.level(), CompleteSelection.from(selection.dimension, selection.first, selection.second));
			} else if (selection.first != null) {
				drawSinglePoint(player.level(), selection.first, ParticleTypes.END_ROD);
			} else if (selection.second != null) {
				drawSinglePoint(player.level(), selection.second, ParticleTypes.ENCHANT);
			}
		}
	}

	public static void clear(UUID playerId) {
		SELECTIONS.remove(playerId);
	}

	private static void setCorner(ServerPlayer player, BlockPos pos, boolean first) {
		GrandBuilderConfig config = GrandBuilderConfig.get();
		if (!config.hasPermission(player)) {
			player.displayClientMessage(Component.translatable("message.grand_builder.no_permission"), true);
			return;
		}
		if (!config.isDimensionAllowed(player.level().dimension())) {
			player.displayClientMessage(Component.translatable("message.grand_builder.dimension_blocked"), true);
			return;
		}

		MutableSelection selection = SELECTIONS.computeIfAbsent(player.getUUID(), ignored -> new MutableSelection(player.level().dimension()));
		if (!selection.dimension.equals(player.level().dimension())) {
			selection.dimension = player.level().dimension();
			selection.first = null;
			selection.second = null;
		}

		if (first) {
			selection.first = pos.immutable();
			player.displayClientMessage(Component.translatable("message.grand_builder.selection_first", formatPos(pos)), true);
		} else {
			selection.second = pos.immutable();
			player.displayClientMessage(Component.translatable("message.grand_builder.selection_second", formatPos(pos)), true);
		}

		player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55f, first ? 1.25f : 0.85f);
		drawSinglePoint(player.level(), pos, first ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT);

		if (selection.first != null && selection.second != null) {
			CompleteSelection complete = CompleteSelection.from(selection.dimension, selection.first, selection.second);
			player.displayClientMessage(Component.translatable(
				"message.grand_builder.selection_ready",
				complete.width(),
				complete.height(),
				complete.depth(),
				complete.volume()
			), false);
		}
	}

	private static boolean isHoldingSelector(ServerPlayer player) {
		return player.getMainHandItem().is(GrandBuilderMod.STRUCTURE_SELECTOR)
			|| player.getOffhandItem().is(GrandBuilderMod.STRUCTURE_SELECTOR);
	}

	private static Component formatPos(BlockPos pos) {
		return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
	}

	private static void drawSinglePoint(ServerLevel level, BlockPos pos, ParticleOptions particle) {
		for (Direction direction : Direction.values()) {
			BlockPos marker = pos.relative(direction);
			level.sendParticles(particle, marker.getX() + 0.5, marker.getY() + 0.5, marker.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.0);
	}

	private static void drawSelectionBox(ServerLevel level, CompleteSelection selection) {
		int minX = selection.min().getX();
		int minY = selection.min().getY();
		int minZ = selection.min().getZ();
		int maxX = selection.max().getX();
		int maxY = selection.max().getY();
		int maxZ = selection.max().getZ();
		int step = edgeStep(selection.width(), selection.height(), selection.depth());

		drawEdge(level, minX, minY, minZ, maxX, minY, minZ, step, ParticleTypes.END_ROD);
		drawEdge(level, minX, minY, maxZ, maxX, minY, maxZ, step, ParticleTypes.END_ROD);
		drawEdge(level, minX, maxY, minZ, maxX, maxY, minZ, step, ParticleTypes.ENCHANT);
		drawEdge(level, minX, maxY, maxZ, maxX, maxY, maxZ, step, ParticleTypes.ENCHANT);

		drawEdge(level, minX, minY, minZ, minX, minY, maxZ, step, ParticleTypes.END_ROD);
		drawEdge(level, maxX, minY, minZ, maxX, minY, maxZ, step, ParticleTypes.END_ROD);
		drawEdge(level, minX, maxY, minZ, minX, maxY, maxZ, step, ParticleTypes.ENCHANT);
		drawEdge(level, maxX, maxY, minZ, maxX, maxY, maxZ, step, ParticleTypes.ENCHANT);

		drawEdge(level, minX, minY, minZ, minX, maxY, minZ, step, ParticleTypes.CRIT);
		drawEdge(level, maxX, minY, minZ, maxX, maxY, minZ, step, ParticleTypes.CRIT);
		drawEdge(level, minX, minY, maxZ, minX, maxY, maxZ, step, ParticleTypes.CRIT);
		drawEdge(level, maxX, minY, maxZ, maxX, maxY, maxZ, step, ParticleTypes.CRIT);
	}

	private static int edgeStep(int width, int height, int depth) {
		int longest = Math.max(width, Math.max(height, depth));
		return Math.max(1, (longest + MAX_EDGE_SAMPLES - 1) / MAX_EDGE_SAMPLES);
	}

	private static void drawEdge(
		ServerLevel level,
		int startX,
		int startY,
		int startZ,
		int endX,
		int endY,
		int endZ,
		int step,
		ParticleOptions particle
	) {
		int dx = Integer.compare(endX, startX);
		int dy = Integer.compare(endY, startY);
		int dz = Integer.compare(endZ, startZ);
		int length = Math.max(Math.abs(endX - startX), Math.max(Math.abs(endY - startY), Math.abs(endZ - startZ)));

		for (int distance = 0; distance <= length; distance += step) {
			spawnMarker(level, particle, startX + dx * distance, startY + dy * distance, startZ + dz * distance);
		}
		spawnMarker(level, particle, endX, endY, endZ);
	}

	private static void spawnMarker(ServerLevel level, ParticleOptions particle, int x, int y, int z) {
		level.sendParticles(particle, x + 0.5, y + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
	}

	private static final class MutableSelection {
		private ResourceKey<Level> dimension;
		private BlockPos first;
		private BlockPos second;

		private MutableSelection(ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}
	}

	public record CompleteSelection(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
		private static CompleteSelection from(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
			BlockPos min = new BlockPos(
				Math.min(first.getX(), second.getX()),
				Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ())
			);
			BlockPos max = new BlockPos(
				Math.max(first.getX(), second.getX()),
				Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ())
			);
			return new CompleteSelection(dimension, min, max);
		}

		public int width() {
			return max.getX() - min.getX() + 1;
		}

		public int height() {
			return max.getY() - min.getY() + 1;
		}

		public int depth() {
			return max.getZ() - min.getZ() + 1;
		}

		public long volume() {
			return (long) width() * height() * depth();
		}
	}
}
