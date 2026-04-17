package dev.grandbuilder.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class GrandPalaceBlueprint {
	private GrandPalaceBlueprint() {
	}

	public static List<RelativeBlock> create() {
		Map<Offset, BlockState> blocks = new LinkedHashMap<>();

		buildFoundation(blocks);
		buildOuterWalls(blocks);
		buildCornerTowers(blocks);
		buildCourtyardColumns(blocks);
		buildCentralPalace(blocks);
		buildDecorativeLights(blocks);
		buildGardens(blocks);

		List<RelativeBlock> ordered = new ArrayList<>(blocks.size());
		for (Map.Entry<Offset, BlockState> entry : blocks.entrySet()) {
			Offset offset = entry.getKey();
			ordered.add(new RelativeBlock(
				offset.x(),
				offset.y(),
				offset.z(),
				entry.getValue(),
				stageFor(offset)
			));
		}

		ordered.sort(Comparator.comparingInt(RelativeBlock::stage));
		return ordered;
	}

	private static void buildFoundation(Map<Offset, BlockState> blocks) {
		for (int x = -18; x <= 18; x++) {
			for (int z = -18; z <= 18; z++) {
				int distanceSq = x * x + z * z;

				if (distanceSq <= 18 * 18) {
					set(blocks, x, 0, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				}

				if (distanceSq <= 16 * 16) {
					set(blocks, x, 1, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				}

				if (distanceSq <= 13 * 13) {
					BlockState tile = ((x + z) & 1) == 0
						? Blocks.SMOOTH_QUARTZ.defaultBlockState()
						: Blocks.CALCITE.defaultBlockState();
					set(blocks, x, 2, z, tile);
				}
			}
		}
	}

	private static void buildOuterWalls(Map<Offset, BlockState> blocks) {
		int min = -14;
		int max = 14;

		for (int y = 3; y <= 11; y++) {
			for (int i = min; i <= max; i++) {
				boolean frontGateOpening = Math.abs(i) <= 3 && y <= 8;
				if (!frontGateOpening) {
					set(blocks, i, y, min, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				}

				set(blocks, i, y, max, Blocks.DEEPSLATE_BRICKS.defaultBlockState());

				boolean sideOpening = Math.abs(i) <= 2 && y <= 7;
				if (!sideOpening) {
					set(blocks, min, y, i, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
					set(blocks, max, y, i, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				}
			}
		}

		for (int i = min; i <= max; i++) {
			if ((i & 1) == 0) {
				set(blocks, i, 12, min, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
				set(blocks, i, 12, max, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
				set(blocks, min, 12, i, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
				set(blocks, max, 12, i, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
			}
		}
	}

	private static void buildCornerTowers(Map<Offset, BlockState> blocks) {
		int[] points = {-14, 14};

		for (int towerX : points) {
			for (int towerZ : points) {
				for (int y = 3; y <= 17; y++) {
					for (int dx = -3; dx <= 3; dx++) {
						for (int dz = -3; dz <= 3; dz++) {
							int distSq = dx * dx + dz * dz;
							if (distSq >= 6 && distSq <= 9) {
								set(blocks,
									towerX + dx,
									y,
									towerZ + dz,
									Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
							}
						}
					}
				}

				for (int y = 18; y <= 22; y++) {
					int shrink = y - 18;
					int radius = 3 - shrink;
					for (int dx = -radius; dx <= radius; dx++) {
						for (int dz = -radius; dz <= radius; dz++) {
							if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
								set(blocks,
									towerX + dx,
									y,
									towerZ + dz,
									Blocks.WAXED_CUT_COPPER.defaultBlockState());
							}
						}
					}
				}

				set(blocks, towerX, 23, towerZ, Blocks.GOLD_BLOCK.defaultBlockState());
			}
		}
	}

	private static void buildCourtyardColumns(Map<Offset, BlockState> blocks) {
		for (int x = -10; x <= 10; x += 5) {
			for (int z = -10; z <= 10; z += 5) {
				for (int y = 3; y <= 8; y++) {
					set(blocks, x, y, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
				}
			}
		}
	}

	private static void buildCentralPalace(Map<Offset, BlockState> blocks) {
		for (int x = -7; x <= 7; x++) {
			for (int z = -9; z <= 9; z++) {
				set(blocks, x, 3, z, Blocks.SMOOTH_STONE.defaultBlockState());
			}
		}

		for (int y = 4; y <= 15; y++) {
			for (int x = -7; x <= 7; x++) {
				for (int z = -9; z <= 9; z++) {
					boolean edge = Math.abs(x) == 7 || Math.abs(z) == 9;
					if (!edge) {
						continue;
					}

					if (z == -9 && Math.abs(x) <= 2 && y <= 8) {
						continue;
					}

					boolean window = y >= 8 && y <= 11
						&& (Math.abs(x) == 7 || Math.abs(z) == 9)
						&& ((x + z) % 4 == 0);

					set(blocks, x, y, z, window
						? Blocks.TINTED_GLASS.defaultBlockState()
						: Blocks.QUARTZ_BRICKS.defaultBlockState());
				}
			}
		}

		for (int y = 16; y <= 21; y++) {
			int shrink = y - 16;
			int maxX = 7 - shrink;
			int maxZ = 9 - shrink;

			for (int x = -maxX; x <= maxX; x++) {
				for (int z = -maxZ; z <= maxZ; z++) {
					set(blocks, x, y, z, Blocks.CUT_COPPER.defaultBlockState());
				}
			}
		}

		set(blocks, 0, 22, 0, Blocks.BEACON.defaultBlockState());
	}

	private static void buildDecorativeLights(Map<Offset, BlockState> blocks) {
		for (int i = -12; i <= 12; i += 4) {
			set(blocks, i, 7, -13, Blocks.SEA_LANTERN.defaultBlockState());
			set(blocks, i, 7, 13, Blocks.SEA_LANTERN.defaultBlockState());
			set(blocks, -13, 7, i, Blocks.SEA_LANTERN.defaultBlockState());
			set(blocks, 13, 7, i, Blocks.SEA_LANTERN.defaultBlockState());
		}

		for (int i = -6; i <= 6; i += 3) {
			set(blocks, i, 12, -8, Blocks.GLOWSTONE.defaultBlockState());
			set(blocks, i, 12, 8, Blocks.GLOWSTONE.defaultBlockState());
		}
	}

	private static void buildGardens(Map<Offset, BlockState> blocks) {
		int[] points = {-9, 9};
		for (int centerX : points) {
			for (int centerZ : points) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						set(blocks, centerX + dx, 3, centerZ + dz, Blocks.MOSS_BLOCK.defaultBlockState());
					}
				}

				set(blocks, centerX, 4, centerZ, Blocks.FLOWERING_AZALEA.defaultBlockState());
				set(blocks, centerX - 1, 4, centerZ, Blocks.ALLIUM.defaultBlockState());
				set(blocks, centerX + 1, 4, centerZ, Blocks.BLUE_ORCHID.defaultBlockState());
				set(blocks, centerX, 4, centerZ - 1, Blocks.AZURE_BLUET.defaultBlockState());
				set(blocks, centerX, 4, centerZ + 1, Blocks.ORANGE_TULIP.defaultBlockState());
			}
		}
	}

	private static void set(Map<Offset, BlockState> blocks, int x, int y, int z, BlockState state) {
		blocks.put(new Offset(x, y, z), state);
	}

	private static int stageFor(Offset offset) {
		int wave = Math.floorMod(offset.x() * 5 - offset.z() * 3, 11);
		int radial = (int) Math.round(Math.sqrt(offset.x() * offset.x() + offset.z() * offset.z()) * 8);
		return offset.y() * 120 + radial + wave;
	}

	public record RelativeBlock(int x, int y, int z, BlockState state, int stage, CompoundTag blockEntityNbt) {
		public RelativeBlock(int x, int y, int z, BlockState state, int stage) {
			this(x, y, z, state, stage, null);
		}
	}

	private record Offset(int x, int y, int z) {
	}
}
