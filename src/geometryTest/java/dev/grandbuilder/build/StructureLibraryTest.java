package dev.grandbuilder.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import dev.grandbuilder.network.StructurePreviewPayload;

public final class StructureLibraryTest {
	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		verifyDense();
		verifyCompactOrders();
		verifyLitematic();
		verifySnapshots();
		verifyLimits();
		Path temp = Files.createTempDirectory("grand-builder-library-test-");
		try { verifyCatalog(temp); verifyRawNbt(temp); }
		finally {
			try (var paths = Files.walk(temp)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		if (args.length > 0) verifyRealDirectory(Path.of(args[0]));
		System.out.println("Structure library verified: lazy catalogue/cache, compact dense order, air/crop/NBT, malformed data and memory limits.");
	}

	private static void verifyDense() throws Exception {
		int sx = 5, sy = 4, sz = 6;
		byte[] data = new byte[sx * sy * sz];
		data[(1 * sz + 1) * sx + 1] = 1;
		data[(2 * sz + 4) * sx + 3] = 1;
		CompoundTag chest = new CompoundTag();
		chest.putString("id", "minecraft:chest"); chest.putString("CustomName", "Library QA");
		var palette = Map.of(0, Blocks.AIR.defaultBlockState(), 1, Blocks.CHEST.defaultBlockState());
		var entities = Map.of((1L * sz + 1) * sx + 1, chest);
		var dense = DenseStructureBlueprint.sponge(sx, sy, sz, palette, data, entities, false, 1000);
		check(dense.size() == 24 && dense.nonAirCount() == 2, "Trimmed volume or internal air changed");
		List<GrandPalaceBlueprint.RelativeBlock> expected = new ArrayList<>();
		for (int y = 0; y < 2; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 3; x++) {
			int rx = x - 1, rz = z - 2, index = ((y + 1) * sz + z + 1) * sx + x + 1;
			int stage = y * 120 + (int)Math.round(Math.hypot(rx, rz) * 8) + Math.floorMod(rx * 5 - rz * 3, 11);
			expected.add(new GrandPalaceBlueprint.RelativeBlock(rx, y, rz, palette.get((int)data[index]), stage,
				entities.get(((long)(y + 1) * sz + z + 1) * sx + x + 1)));
		}
		expected.sort(Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage));
		check(dense.equals(expected), "Compact decoding changed coordinates, stable stage order or block NBT");
		chest.putString("CustomName", "mutated");
		check(dense.stream().filter(block -> block.blockEntityNbt() != null).findFirst().orElseThrow()
			.blockEntityNbt().getStringOr("CustomName", "").equals("Library QA"), "NBT was not copied");
		var full = DenseStructureBlueprint.sponge(sx, sy, sz, palette, data, Map.of(), true, 1000);
		check(full.size() == data.length && full.width() == sx && full.height() == sy && full.depth() == sz, "Imported air margins lost");
		check(full.sampleNonAir(100).size() == 2 && full.sampleNonAir(1).size() == 1, "Visual budget is spent on air or exceeds its cap");
		for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
			BlockPos origin = new BlockPos(50, 80, -10);
			var bounds = StructureLibrary.boundaryBlocks(full);
			var all = new ArrayList<>(full);
			for (Direction.Axis axis : Direction.Axis.values()) {
				int min = all.stream().mapToInt(block -> AnimatedBuildManager.transform(origin, facing, block).get(axis)).min().orElseThrow();
				int max = all.stream().mapToInt(block -> AnimatedBuildManager.transform(origin, facing, block).get(axis)).max().orElseThrow();
				check(bounds.stream().mapToInt(block -> AnimatedBuildManager.transform(origin, facing, block).get(axis)).min().orElseThrow() == min
					&& bounds.stream().mapToInt(block -> AnimatedBuildManager.transform(origin, facing, block).get(axis)).max().orElseThrow() == max, "Corner bounds differ");
			}
		}
		var twoByte = DenseStructureBlueprint.sponge(1, 1, 1, Map.of(300, Blocks.STONE.defaultBlockState()),
			new byte[] {(byte)0xac, 2}, Map.of(), false, 10);
		check(twoByte.getFirst().state() == Blocks.STONE.defaultBlockState(), "Two-byte palette index changed");
		reject(() -> DenseStructureBlueprint.sponge(2, 1, 1, palette, new byte[] {1}, Map.of(), false, 10));
		reject(() -> DenseStructureBlueprint.sponge(1, 1, 1, palette, new byte[] {1, 0}, Map.of(), false, 10));
		reject(() -> DenseStructureBlueprint.sponge(1, 1, 1, palette, new byte[] {2}, Map.of(), false, 10));
		reject(() -> DenseStructureBlueprint.sponge(1, 1, 1, palette, new byte[] {(byte)0x80}, Map.of(), false, 10));
		reject(() -> DenseStructureBlueprint.checkedVolume(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
		reject(() -> DenseStructureBlueprint.checkedVolume(65535, 65535, 65535, 50_000_000));
		reject(() -> DenseStructureBlueprint.checkedVolume(-1, 20, 20, 1000));
		reject(() -> DenseStructureBlueprint.sponge(50_000_000, 1, 1, palette, new byte[] {1}, Map.of(), false, 50_000_000));
	}

	private static void verifyCompactOrders() throws Exception {
		for (int[] size : List.of(new int[] {1, 1, 1}, new int[] {7, 4, 5}, new int[] {103, 5, 57}, new int[] {2, 20, 3})) {
			int w = size[0], h = size[1], d = size[2], total = w * h * d;
			byte[] data = new byte[total];
			for (int i = 0; i < total; i++) data[i] = (byte)(i % 3);
			if (total == 1) data[0] = 1;
			var source = DenseStructureBlueprint.sponge(w, h, d, Map.of(0, Blocks.AIR.defaultBlockState(),
				1, Blocks.BRICKS.defaultBlockState(), 2, Blocks.WATER.defaultBlockState()), data, Map.of(), true, 1_000_000);
			List<GrandPalaceBlueprint.RelativeBlock> expected = new ArrayList<>();
			for (int i = 0; i < total; i++) expected.add(source.atFlat(i));
			var library = new ArrayList<>(expected);
			library.sort(Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage));
			check(source.equals(library), "Compact column order differs at " + Arrays.toString(size));
			for (boolean xFirst : new boolean[] {false, true}) {
				var order = new CompactStageOrder(w, h, d, xFirst);
				for (int i = 0; i < total; i++) check(order.orderedIndex(order.flatIndex(i)) == i, "Compact rank inverse changed");
			}
			for (int mode = -1; mode < BuildStartSide.values().length; mode++) {
				BuildStartSide side = mode < 0 ? null : BuildStartSide.values()[mode];
				var plan = new DenseBuildPlan(source, side);
				var comparator = side == null ? Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage)
					.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::y).thenComparingInt(GrandPalaceBlueprint.RelativeBlock::x)
					.thenComparingInt(GrandPalaceBlueprint.RelativeBlock::z) : side.comparator();
				expected.sort(comparator);
				check(plan.dry().equals(expected.stream().filter(block -> block.state().getFluidState().isEmpty()).toList()), "Dry compact view differs: " + side);
				check(plan.fluid().equals(expected.stream().filter(block -> !block.state().getFluidState().isEmpty()).toList()), "Fluid compact view differs: " + side);
			}
			for (int cap : new int[] {1, 13, total}) {
				var indices = source.sampleNonAirIndexes(cap);
				check(indices.length <= cap && indices.length > 0, "Unbounded preview indices");
				for (int index : indices) check(!source.get(index).state().isAir(), "Sampled preview air");
				var surface = source.sampleSurface(cap);
				check(surface.size() <= cap && surface.stream().noneMatch(block -> block.state().isAir()), "Unbounded surface sample");
			}
		}
	}

	private static CompoundTag litematicRegion(int sx, int sy, int sz, int px, int py, int pz, int[] values) {
		CompoundTag region = new CompoundTag();
		CompoundTag size = new CompoundTag(), pos = new CompoundTag();
		size.putInt("x", sx); size.putInt("y", sy); size.putInt("z", sz);
		pos.putInt("x", px); pos.putInt("y", py); pos.putInt("z", pz);
		region.put("Size", size); region.put("Position", pos);
		ListTag palette = new ListTag();
		for (String id : List.of("minecraft:air", "minecraft:bricks", "minecraft:chest", "minecraft:water", "minecraft:stone")) {
			CompoundTag state = new CompoundTag(); state.putString("Name", id); palette.add(state);
		}
		region.put("BlockStatePalette", palette);
		long[] packed = new long[(values.length * 3 + 63) / 64];
		for (int i = 0; i < values.length; i++) {
			int bit = i * 3, word = bit / 64, shift = bit % 64;
			packed[word] |= (long)values[i] << shift;
			if (shift > 61) packed[word + 1] |= (long)values[i] >>> (64 - shift);
		}
		region.putLongArray("BlockStates", packed);
		return region;
	}
	private static void verifyLitematic() throws Exception {
		CompoundTag root = new CompoundTag(), regions = new CompoundTag(); root.put("Regions", regions);
		int[] values = new int[4 * 3 * 6];
		for (int i = 0; i < values.length; i++) values[i] = i % 5;
		CompoundTag region = litematicRegion(-4, -3, -6, 100, 20, -10, values);
		ListTag entities = new ListTag(); CompoundTag chest = new CompoundTag();
		chest.putString("id", "minecraft:chest"); chest.putString("CustomName", "Negative corner");
		chest.putInt("x", 2); chest.putInt("y", 0); chest.putInt("z", 0); entities.add(chest); region.put("TileEntities", entities);
		regions.put("a", region);
		var dense = StructureLibrary.denseLitematic(root, true);
		var states = List.of(Blocks.AIR.defaultBlockState(), Blocks.BRICKS.defaultBlockState(), Blocks.CHEST.defaultBlockState(),
			Blocks.WATER.defaultBlockState(), Blocks.STONE.defaultBlockState());
		for (int i = 0; i < values.length; i++) check(dense.atFlat(i).state() == states.get(values[i]), "Negative region mirrored or packed word crossing failed");
		check(dense.atFlat(2).blockEntityNbt().getStringOr("CustomName", "").equals("Negative corner"), "Negative region lost local tile coordinates");
		regions.put("b", litematicRegion(2, 1, 1, 97, 18, -15, new int[] {4, 4}));
		var overlap = StructureLibrary.denseLitematic(root, true);
		check(overlap.atFlat(0).state() == Blocks.STONE.defaultBlockState() && overlap.atFlat(1).state() == Blocks.STONE.defaultBlockState(), "Overlap precedence changed");
		regions.put("c", litematicRegion(1, 1, 1, 99, 18, -15, new int[] {0}));
		check(StructureLibrary.denseLitematic(root, true).atFlat(2).blockEntityNbt() == null, "Overlapping air retained an obsolete tile");
		regions.put("d", litematicRegion(1, 1, 1, 106, 18, -15, new int[] {1}));
		var gap = StructureLibrary.denseLitematic(root, true);
		check(gap.width() == 10 && gap.atFlat(7).state().isAir(), "Multi-region bounds or intervening air lost");
		regions.put("bad", litematicRegion(1, 1, 1, 0, 0, 0, new int[] {7}));
		reject(() -> StructureLibrary.denseLitematic(root, true));
		regions.remove("bad");
		region.putLongArray("BlockStates", new long[1]); reject(() -> StructureLibrary.denseLitematic(root, true));
		var tallRoot = new CompoundTag(); var tallRegions = new CompoundTag(); tallRoot.put("Regions", tallRegions);
		int[] tallValues = new int[5000]; tallValues[4999] = 2;
		var tall = litematicRegion(1, 5000, 1, 0, 0, 0, tallValues);
		var tallEntities = new ListTag(); var tallChest = chest.copy(); tallChest.putInt("x", 0); tallChest.putInt("y", 4999); tallEntities.add(tallChest);
		tall.put("TileEntities", tallEntities); tallRegions.put("tall", tall);
		check(StructureLibrary.denseLitematic(tallRoot, true).atFlat(4999).blockEntityNbt() != null, "Tall tile positions wrapped to twelve bits");
	}
	private static void verifySnapshots() {
		var compact = new CompactSnapshotMap(8 * 1024 * 1024);
		Map<BlockPos, AnimatedBuildManager.SnapshotBlock> expected = new java.util.HashMap<>();
		for (int x = -16; x < 16; x++) for (int y = -16; y < 16; y++) for (int z = -16; z < 16; z++) {
			var pos = new BlockPos(x, y, z);
			var block = new AnimatedBuildManager.SnapshotBlock((x + y + z) % 3 == 0 ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), null);
			expected.put(pos, block); compact.put(pos, block);
		}
		CompoundTag nbt = new CompoundTag(); nbt.putString("id", "minecraft:chest");
		var tile = new AnimatedBuildManager.SnapshotBlock(Blocks.CHEST.defaultBlockState(), nbt);
		compact.put(BlockPos.ZERO, tile); expected.put(BlockPos.ZERO, tile);
		check(compact.equals(expected) && expected.equals(compact), "Compact undo changed air/NBT/negative coordinates or iteration");
		check(compact.retainedBytes() < expected.size() * 32L, "Undo still retains per-block objects");
		var tiny = new CompactSnapshotMap(16_512);
		tiny.put(BlockPos.ZERO, new AnimatedBuildManager.SnapshotBlock(Blocks.AIR.defaultBlockState(), null));
		try { tiny.put(new BlockPos(16, 0, 0), tile); throw new AssertionError("Undo budget ignored"); }
		catch (CompactSnapshotMap.BudgetExceeded expectedFailure) { check(tiny.size() == 1 && !tiny.containsKey(new BlockPos(16, 0, 0)), "Budget refusal mutated undo"); }
	}
	private static void verifyLimits() throws Exception {
		check(DenseStructureBlueprint.checkedVolume(1000, 1000, 1000, 1_000_000_000) == 1_000_000_000, "Billion-cell cap missing");
		reject(() -> DenseStructureBlueprint.checkedVolume(1001, 1000, 1000, 1_000_000_000));
		var config = new dev.grandbuilder.config.GrandBuilderConfig();
		var sanitize = config.getClass().getDeclaredMethod("sanitize"); sanitize.setAccessible(true);
		config.maxBlocksPerBuild = 50_000_000; config.maxPreviewBlocks = 50_000_000; sanitize.invoke(config);
		check(config.maxBlocksPerBuild == 1_000_000_000 && config.maxPreviewBlocks == 1_000_000_000, "Old defaults not migrated");
		config.maxBlocksPerBuild = 123456; config.maxPreviewBlocks = 65432; sanitize.invoke(config);
		check(config.maxBlocksPerBuild == 123456 && config.maxPreviewBlocks == 65432, "Custom server limits overwritten");
		check(!LargePreviewGuard.needsWarning(4_999_999, 999_999) && LargePreviewGuard.needsWarning(5_000_000, 2)
			&& LargePreviewGuard.needsWarning(1_000_001, 1_000_000), "Large preview warning thresholds changed");
	}

	private static CompoundTag fixture() {
		CompoundTag root = new CompoundTag();
		root.putInt("Version", 2); root.putShort("Width", (short)3); root.putShort("Height", (short)2); root.putShort("Length", (short)2);
		CompoundTag palette = new CompoundTag();
		palette.putInt("minecraft:air", 0); palette.putInt("minecraft:bricks", 1);
		root.put("Palette", palette);
		root.putByteArray("BlockData", new byte[] {1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1});
		root.put("BlockEntities", new ListTag());
		return root;
	}

	private static void verifyCatalog(Path temp) throws Exception {
		Path dir = Files.createDirectory(temp.resolve("catalog"));
		Path valid = dir.resolve("house_6.schem"), broken = dir.resolve("broken.schem");
		NbtIo.writeCompressed(fixture(), valid);
		Files.write(broken, new byte[] {0, 1, 2});
		var catalog = new StructureLibrary.ExternalCatalog(List.of(dir), () -> 512);
		var sources = catalog.sources();
		check(sources.size() == 2 && sources.containsKey("file:broken"), "Listing parsed NBT instead of listing files");
		var source = sources.get("file:house_6");
		var first = catalog.resolve(source);
		check(first != null && first.blueprint() instanceof DenseStructureBlueprint, "Selected schematic is not compact");
		check(first.blueprint().size() == 12 && StructureLibrary.countNonAir(first.blueprint()) == 3, "Internal air or non-air count changed");
		for (int i = 0; i < 10; i++) {
			catalog.refresh();
			check(catalog.resolve(catalog.sources().get(source.key())) == first, "Metadata refresh redecoded an unchanged file");
		}
		var brokenSource = sources.get("file:broken");
		check(catalog.resolve(brokenSource) == null, "Corrupt schematic accepted");
		NbtIo.writeCompressed(fixture(), broken);
		check(catalog.resolve(brokenSource) == null, "Failed source retried without a metadata change");
		catalog.refresh();
		check(catalog.resolve(catalog.sources().get("file:broken")) != null, "Repaired file not invalidated");
		check(catalog.resolve(source) != first, "Catalogue retained multiple blueprints");
		var before = catalog.resolve(source);
		Files.setLastModifiedTime(valid, FileTime.fromMillis(Files.getLastModifiedTime(valid).toMillis() + 10_000));
		catalog.refresh();
		check(catalog.resolve(catalog.sources().get(source.key())) != before, "Modified source reused stale blocks");
		Files.delete(broken);
		Path pack = Files.createDirectory(dir.resolve("Pack"));
		NbtIo.writeCompressed(fixture(), pack.resolve("part_x0_y0_z0.schem"));
		NbtIo.writeCompressed(fixture(), pack.resolve("part_x3_y0_z0.schem"));
		catalog.refresh();
		check(catalog.sources().size() == 2 && catalog.resolve(catalog.sources().get("file:pack")).blueprint().size() == 24, "Pack offsets or dedupe changed");
		NbtIo.writeCompressed(fixture(), dir.resolve("group_x0_y0_z0.schem"));
		NbtIo.writeCompressed(fixture(), dir.resolve("group_x3_y0_z0.schem"));
		catalog.refresh();
		check(catalog.resolve(catalog.sources().get("file:group")).blueprint().size() == 24, "Top-level multi-piece grouping changed");
		var capped = new StructureLibrary.ExternalCatalog(List.of(dir), () -> 2);
		check(capped.sources().values().stream().mapToInt(entry -> entry.files().size()).sum() <= 2, "Global scan cap exceeded by a pack");
		Path empty = Files.createDirectory(temp.resolve("empty"));
		var emptyCatalog = new StructureLibrary.ExternalCatalog(List.of(empty), () -> 512);
		var emptyIndex = emptyCatalog.sources();
		NbtIo.writeCompressed(fixture(), empty.resolve("new.schem"));
		check(emptyCatalog.sources() == emptyIndex, "Empty catalogue ignored its TTL");
		emptyCatalog.refresh(); check(emptyCatalog.sources().size() == 1, "New file not listed after refresh");
	}

	private static void verifyRawNbt(Path temp) throws Exception {
		Path raw = temp.resolve("raw.schem"); NbtIo.write(fixture(), raw);
		check(StructureLibrary.denseSponge(StructureLibrary.readNbtAuto(raw), false).size() == 12, "Raw NBT detection failed");
		var output = new java.io.ByteArrayOutputStream();
		try (var tags = new java.io.DataOutputStream(output)) {
			tags.writeByte(10); tags.writeUTF("");
			tags.writeByte(7); tags.writeUTF("BlockData"); tags.writeInt(256 * 1024 * 1024);
		}
		Path overBudget = temp.resolve("over-budget.schem");
		Files.write(overBudget, output.toByteArray());
		reject(() -> StructureLibrary.readNbtAuto(overBudget));
		CompoundTag oversized = fixture();
		oversized.putInt("Width", 100000); oversized.putInt("Height", 100000); oversized.putInt("Length", 100000);
		reject(() -> StructureLibrary.denseSponge(oversized, false));
	}

	private static void verifyRealDirectory(Path directory) throws Exception {
		var catalog = new StructureLibrary.ExternalCatalog(List.of(directory), () -> 512);
		long start = System.nanoTime();
		var sources = catalog.sources();
		System.out.printf("Real catalogue: %d entries in %.1f ms (no NBT decoded)%n", sources.size(), (System.nanoTime() - start) / 1e6);
		for (var source : sources.values()) {
			Path path = source.files().getFirst().path();
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
			FileTime timestamp = Files.getLastModifiedTime(path);
			start = System.nanoTime();
			var selected = catalog.resolve(source);
			check(selected != null, "Real file failed: " + path);
			long retained = selected.blueprint() instanceof DenseStructureBlueprint dense ? dense.retainedBytes() : -1;
			System.out.printf("%s: %,d positions, %,d solid, %.1f MiB retained, %.2f s%n", source.displayName(), selected.blueprint().size(),
				StructureLibrary.countNonAir(selected.blueprint()), retained / 1048576.0, (System.nanoTime() - start) / 1e9);
			catalog.refresh();
			check(catalog.resolve(catalog.sources().get(source.key())) == selected, "Real file reloaded on metadata refresh");
			if (source.key().equals("file:the-illinois")) {
				check(selected.blueprint().size() == 25_481_470 && retained < 128L * 1024 * 1024, "Illinois unexpectedly expanded");
				var preview = StructurePreviewSampler.blueprint(StructurePreviewPayload.INSPECT,
					net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), new BlockPos(0,80,0), Direction.SOUTH, selected.blueprint());
				check(preview.cells().size() > 1000 && preview.cells().size() <= StructurePreviewPayload.MAX_CELLS, "Large inspection is empty or unbounded");
				System.out.println("Illinois 3D sample: " + preview.cells().size() + " cells");
			}
			if (selected.blueprint() instanceof DenseStructureBlueprint dense && dense.size() > 5_000_000) {
				start = System.nanoTime();
				var plan = new DenseBuildPlan(dense, null);
				check(plan.dry().size() + plan.fluid().size() == dense.size(), "Real build lost air or fluid work");
				check(plan.retainedBytes() < dense.size() / 2L, "Real build expanded into per-cell indices/objects");
				check(!plan.dry().isEmpty() && plan.dry().getFirst().state().getFluidState().isEmpty(), "Real dry view starts with fluid");
				System.out.printf("Compact build plan: %.1f MiB, %.2f s%n", plan.retainedBytes() / 1048576.0, (System.nanoTime() - start) / 1e9);
			}
			check(timestamp.equals(Files.getLastModifiedTime(path)) && Arrays.equals(hash,
				MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))), "Source file changed");
		}
	}

	private static void reject(Checked action) throws Exception {
		try { action.run(); throw new AssertionError("Unsafe/malformed schematic accepted"); }
		catch (IOException expected) { }
	}
	private interface Checked { void run() throws Exception; }
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
