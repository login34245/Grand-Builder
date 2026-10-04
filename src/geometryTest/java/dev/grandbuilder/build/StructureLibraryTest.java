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
		var entities = Map.of(BlockPos.asLong(1, 1, 1), chest);
		var dense = DenseStructureBlueprint.sponge(sx, sy, sz, palette, data, entities, false, 1000);
		check(dense.size() == 24 && dense.nonAirCount() == 2, "Trimmed volume or internal air changed");
		List<GrandPalaceBlueprint.RelativeBlock> expected = new ArrayList<>();
		for (int y = 0; y < 2; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 3; x++) {
			int rx = x - 1, rz = z - 2, index = ((y + 1) * sz + z + 1) * sx + x + 1;
			int stage = y * 120 + (int)Math.round(Math.hypot(rx, rz) * 8) + Math.floorMod(rx * 5 - rz * 3, 11);
			expected.add(new GrandPalaceBlueprint.RelativeBlock(rx, y, rz, palette.get((int)data[index]), stage,
				entities.get(BlockPos.asLong(x + 1, y + 1, z + 1))));
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
