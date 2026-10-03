package dev.grandbuilder.build;

import dev.grandbuilder.network.WorldImportStatePayload;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldMapImporterTest {
	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Path temporary = Files.createTempDirectory("grand-builder-import-test-");
		try {
			verifyHouse(temporary);
			verifySkyline(temporary);
			verifyOversizedBuilding(temporary);
			verifyDensePalette(temporary);
			verifyNaturalGround(temporary);
			verifyUnreadableMap(temporary);
			verifyChunkLimit(temporary);
			verifyPreviewShell();
			verifyCodec();
		} finally {
			try (var paths = Files.walk(temporary)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		if (args.length > 0) verifyMap(Path.of(args[0]));
		System.out.println("World import verified: house, connected skyline, oversized sections, dense palettes, folder/ZIP, crop/NBT, errors and codec.");
	}

	private static void verifyHouse(Path temporary) throws Exception {
		Path world = temporary.resolve("house");
		List<BlockState> palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
			Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState(), Blocks.CHEST.defaultBlockState());
		var area = new WorldMapImporter.Bounds(0, 96, 0, 15, 111, 15);
		writeWorld(world, area, palette, (x, y, z) -> {
			if (x == 4 && y == 101 && z == 4) return 4;
			if (x < 2 || x > 12 || z < 2 || z > 12) return 0;
			if (y == 100 || y == 107) return 2;
			if (y > 100 && y < 107 && (x == 2 || x == 12 || z == 2 || z == 12)) return y == 104 ? 3 : 1;
			return 0;
		}, false);
		var source = source(world);
		var candidates = WorldMapImporter.scan(source);
		check(candidates.size() == 1 && !candidates.getFirst().partial(), "House not found");
		var crop = candidates.getFirst().bounds();
		check(crop.equals(new WorldMapImporter.Bounds(0, 98, 0, 14, 109, 14)), "House crop changed");
		var loaded = WorldMapImporter.load(source, crop);
		var capture = WorldMapImporter.capture(loaded, crop);
		check(capture.size() == crop.volume() && capture.stream().anyMatch(block -> block.state().isAir()), "Crop margins lost");
		check(capture.stream().anyMatch(block -> block.blockEntityNbt() != null
			&& block.blockEntityNbt().getStringOr("CustomName", "").equals("Import Test Chest")), "Chest NBT lost");
		check(WorldMapImporter.capture(loaded, crop.adjust(1, -1)).size() == crop.adjust(1, -1).volume(), "Crop resize failed");
		Path zip = temporary.resolve("house.zip");
		try (var output = new ZipOutputStream(Files.newOutputStream(zip)); var files = Files.walk(world)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				output.putNextEntry(new ZipEntry("nested/" + world.relativize(file).toString().replace('\\', '/')));
				Files.copy(file, output);
				output.closeEntry();
			}
			output.putNextEntry(new ZipEntry("nested/DIM-1/region/r.0.0.mca"));
			output.write(new byte[32]);
			output.closeEntry();
		}
		var zipped = new WorldMapImporter.Source("zip", "ZIP", zip, true);
		check(WorldMapImporter.scan(zipped).equals(candidates), "Nested ZIP or dimension filtering failed");
		check(WorldMapImporter.load(zipped, crop).blocks().equals(loaded.blocks()), "ZIP block states/NBT differ");
	}

	private static void verifySkyline(Path temporary) throws Exception {
		Path world = temporary.resolve("skyline");
		var first = new WorldMapImporter.Bounds(8, 65, 8, 23, 264, 23);
		var second = new WorldMapImporter.Bounds(64, 65, 64, 79, 284, 79);
		writeWorld(world, new WorldMapImporter.Bounds(0, 64, 0, 127, 284, 127),
			List.of(Blocks.AIR.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState()),
			(x, y, z) -> y == 64 ? 1 : first.contains(new BlockPos(x, y, z)) || second.contains(new BlockPos(x, y, z)) ? 2 : 0, false);
		var source = source(world);
		var candidates = WorldMapImporter.scan(source);
		check(candidates.size() == 2, "Plaza must not merge tall buildings");
		for (var tower : List.of(first, second)) {
			var candidate = candidates.stream().filter(entry -> WorldMapImporter.inside(tower, entry.bounds())).findFirst().orElseThrow();
			check(!candidate.partial() && candidate.bounds().height() > 128 && candidate.bounds().valid(), "Tall building was discarded or cut");
			var loaded = WorldMapImporter.load(source, candidate.bounds());
			check(loaded.at(new BlockPos(tower.minX(), tower.maxY(), tower.minZ())).state().is(Blocks.IRON_BLOCK), "Tower top missing");
		}
		check(WorldMapImporter.scan(source).equals(candidates), "Candidate order is not deterministic");
		Path wide = temporary.resolve("wide");
		writeWorld(wide, new WorldMapImporter.Bounds(-160, 64, 0, 15, 69, 15),
			List.of(Blocks.AIR.defaultBlockState(), Blocks.BRICKS.defaultBlockState()), (x, y, z) -> 1, false);
		check(WorldMapImporter.scan(source(wide)).getFirst().bounds().width() > 128, "Wide buildings still capped at 128");
	}

	private static void verifyOversizedBuilding(Path temporary) throws Exception {
		Path world = temporary.resolve("oversized");
		writeWorld(world, new WorldMapImporter.Bounds(0, 0, 0, 63, 299, 63),
			List.of(Blocks.AIR.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState()), (x, y, z) -> 1, false);
		var candidates = WorldMapImporter.scan(source(world));
		check(candidates.size() >= 2 && candidates.stream().allMatch(candidate -> candidate.partial() && candidate.bounds().valid()),
			"Oversized building must offer labelled bounded sections");
		for (var corner : List.of(BlockPos.ZERO, new BlockPos(63, 299, 63))) {
			check(candidates.stream().anyMatch(candidate -> candidate.bounds().contains(corner)), "Oversized building section missing");
		}
	}

	private static void verifyDensePalette(Path temporary) throws Exception {
		List<BlockState> palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
			Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState(), Blocks.CHEST.defaultBlockState(),
			Blocks.IRON_BLOCK.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(),
			Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(),
			Blocks.COPPER_BLOCK.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(),
			Blocks.RED_WOOL.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), Blocks.FURNACE.defaultBlockState());
		var area = new WorldMapImporter.Bounds(-16, 96, 0, -1, 111, 15);
		for (boolean dense : new boolean[] {false, true}) {
			Path world = temporary.resolve(dense ? "dense" : "padded");
			writeWorld(world, area, palette, (x, y, z) -> 1 + Math.floorMod(x * 7 + y * 3 + z * 11, 16), dense);
			var source = source(world);
			var candidates = WorldMapImporter.scan(source);
			check(candidates.size() == 1, "Palette building missing");
			var loaded = WorldMapImporter.load(source, candidates.getFirst().bounds());
			for (int y = area.minY(); y <= area.maxY(); y++) for (int z = 0; z < 16; z++) for (int x = -16; x < 0; x++) {
				check(loaded.at(new BlockPos(x, y, z)).state().equals(palette.get(1 + Math.floorMod(x * 7 + y * 3 + z * 11, 16))),
					"Cross-long palette entry decoded incorrectly");
			}
		}
	}

	private static void verifyUnreadableMap(Path temporary) throws Exception {
		Path world = temporary.resolve("unreadable");
		Files.createDirectories(world.resolve("region"));
		ByteBuffer region = ByteBuffer.allocate(12288);
		region.putInt(0, (2 << 8) | 1);
		region.putInt(8192, 2);
		region.put(8196, (byte)99);
		Files.write(world.resolve("region/r.0.0.mca"), region.array());
		try {
			WorldMapImporter.scan(source(world));
			throw new AssertionError("Unreadable map silently reported no buildings");
		} catch (IOException expected) { }
	}

	private static void verifyNaturalGround(Path temporary) throws Exception {
		Path world = temporary.resolve("flat-ground");
		writeWorld(world, new WorldMapImporter.Bounds(0, 0, 0, 127, 3, 127),
			List.of(Blocks.AIR.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()),
			(x, y, z) -> y == 0 ? 1 : y == 3 ? 3 : 2, false);
		check(WorldMapImporter.scan(source(world)).isEmpty(), "Bedrock was mistaken for a bed/building");
	}

	private static void verifyChunkLimit(Path temporary) throws Exception {
		Path world = temporary.resolve("thin-crop");
		var crop = new WorldMapImporter.Bounds(0, 64, 0, 0, 64, 4097 * 16 - 1);
		writeWorld(world, crop, List.of(Blocks.AIR.defaultBlockState(), Blocks.BRICKS.defaultBlockState()), (x, y, z) -> 1, false);
		try {
			WorldMapImporter.load(source(world), crop);
			throw new AssertionError("Chunk limit silently truncated the selected crop");
		} catch (IOException expected) { }
	}

	private static void verifyPreviewShell() {
		Map<Long, BlockState> states = new HashMap<>();
		for (int y = 0; y < 260; y++) for (int x = 0; x < 36; x++) for (int z = 0; z < 36; z++) {
			if (x == 0 || x == 35 || z == 0 || z == 35 || x % 3 == 0 || y % 8 == 0)
				states.put(BlockPos.asLong(x, y, z), Blocks.WHITE_CONCRETE.defaultBlockState());
		}
		var preview = StructurePreviewSampler.fromStates(dev.grandbuilder.network.StructurePreviewPayload.IMPORT,
			net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO, new BlockPos(35, 259, 35), states);
		check(preview.cells().size() <= dev.grandbuilder.network.StructurePreviewPayload.MAX_CELLS, "Preview exceeded packet cap");
		var targets = new java.util.HashSet<Long>();
		for (var cell : preview.cells()) targets.add(cell.target().asLong());
		for (int y = 0; y < 260; y++) for (int a = 0; a < 36; a++) {
			for (var pos : List.of(new BlockPos(0, y, a), new BlockPos(35, y, a), new BlockPos(a, y, 0), new BlockPos(a, y, 35)))
				check(targets.contains(pos.asLong()), "Facade has holes after preview sampling");
		}
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			dev.grandbuilder.network.StructurePreviewPayload.CODEC.encode(buffer, preview);
			check(buffer.readableBytes() < 1024 * 1024, "Preview exceeds Minecraft's custom payload limit");
			check(dev.grandbuilder.network.StructurePreviewPayload.CODEC.decode(buffer).equals(preview) && !buffer.isReadable(), "Large preview codec failed");
		} finally { buffer.release(); }
	}

	private static void verifyCodec() {
		var bounds = new WorldMapImporter.Bounds(-4, 0, -4, 64, 255, 32);
		var payload = new WorldImportStatePayload(List.of(new WorldImportStatePayload.SourceEntry("test", "Map")), "test",
			List.of(new WorldImportStatePayload.CandidateEntry(bounds, 1234, false),
				new WorldImportStatePayload.CandidateEntry(bounds, 987, true)), 1, bounds, WorldImportStatePayload.READY, "");
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			WorldImportStatePayload.CODEC.encode(buffer, payload);
			check(WorldImportStatePayload.CODEC.decode(buffer).equals(payload) && !buffer.isReadable(), "Section label lost in codec");
		} finally { buffer.release(); }
	}

	@FunctionalInterface
	private interface BlockPicker { int at(int x, int y, int z); }

	private static void writeWorld(Path world, WorldMapImporter.Bounds area, List<BlockState> palette, BlockPicker picker, boolean dense) throws IOException {
		Files.createDirectories(world.resolve("region"));
		Map<Long, Map<Integer, byte[]>> regions = new HashMap<>();
		for (int cz = Math.floorDiv(area.minZ(), 16); cz <= Math.floorDiv(area.maxZ(), 16); cz++)
			for (int cx = Math.floorDiv(area.minX(), 16); cx <= Math.floorDiv(area.maxX(), 16); cx++) {
				ListTag sections = new ListTag();
				for (int sy = Math.floorDiv(area.minY(), 16); sy <= Math.floorDiv(area.maxY(), 16); sy++) {
					int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1)), perLong = 64 / bits;
					long[] packed = new long[dense ? (4096 * bits + 63) / 64 : (4096 + perLong - 1) / perLong];
					boolean solid = false;
					for (int index = 0; index < 4096; index++) {
						int x = cx * 16 + (index & 15), z = cz * 16 + ((index >> 4) & 15), y = sy * 16 + (index >> 8);
						int id = area.contains(new BlockPos(x, y, z)) ? picker.at(x, y, z) : 0;
						solid |= id != 0;
						int bitIndex = dense ? index * bits : index / perLong * 64 + index % perLong * bits;
						int li = bitIndex / 64, shift = bitIndex % 64;
						packed[li] |= (long)id << shift;
						if (shift + bits > 64) packed[li + 1] |= (long)id >>> (64 - shift);
					}
					if (!solid) continue;
					CompoundTag section = new CompoundTag(), states = new CompoundTag();
					section.putByte("Y", (byte)sy);
					ListTag tags = new ListTag();
					for (BlockState state : palette) tags.add(NbtUtils.writeBlockState(state));
					if (dense) { section.put("Palette", tags); section.putLongArray("BlockStates", packed); }
					else { states.put("palette", tags); states.putLongArray("data", packed); section.put("block_states", states); }
					sections.add(section);
				}
				if (sections.isEmpty()) continue;
				CompoundTag chunk = new CompoundTag();
				chunk.putInt("xPos", cx); chunk.putInt("zPos", cz);
				chunk.put(dense ? "Sections" : "sections", sections);
				if (cx == 0 && cz == 0 && palette.contains(Blocks.CHEST.defaultBlockState())) {
					CompoundTag chest = new CompoundTag();
					chest.putString("id", "minecraft:chest"); chest.putInt("x", 4); chest.putInt("y", 101); chest.putInt("z", 4);
					chest.putString("CustomName", "Import Test Chest");
					ListTag entities = new ListTag(); entities.add(chest);
					chunk.put(dense ? "TileEntities" : "block_entities", entities);
				}
				if (dense) { CompoundTag root = new CompoundTag(); root.put("Level", chunk); chunk = root; }
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				try (var compressed = new DeflaterOutputStream(bytes); var output = new DataOutputStream(compressed)) { NbtIo.write(chunk, output); }
				long key = ((long)Math.floorDiv(cx, 32) << 32) | (Math.floorDiv(cz, 32) & 0xffffffffL);
				regions.computeIfAbsent(key, ignored -> new HashMap<>()).put(Math.floorMod(cx, 32) + Math.floorMod(cz, 32) * 32, bytes.toByteArray());
			}
		for (var entry : regions.entrySet()) {
			int size = 8192;
			for (byte[] bytes : entry.getValue().values()) size += ((bytes.length + 5 + 4095) / 4096) * 4096;
			ByteBuffer region = ByteBuffer.allocate(size);
			int sector = 2;
			for (var chunk : entry.getValue().entrySet()) {
				int count = (chunk.getValue().length + 5 + 4095) / 4096;
				region.putInt(chunk.getKey() * 4, (sector << 8) | count);
				region.position(sector * 4096); region.putInt(chunk.getValue().length + 1); region.put((byte)2); region.put(chunk.getValue());
				sector += count;
			}
			Files.write(world.resolve("region/r." + (int)(entry.getKey() >> 32) + "." + (int)(long)entry.getKey() + ".mca"), region.array());
		}
		CompoundTag level = new CompoundTag(), data = new CompoundTag();
		data.putInt("SpawnX", area.minX()); data.putInt("SpawnZ", area.minZ()); level.put("Data", data);
		NbtIo.writeCompressed(level, world.resolve("level.dat"));
	}

	private static WorldMapImporter.Source source(Path world) {
		return new WorldMapImporter.Source("test", "Test", world, false);
	}

	private static void verifyMap(Path world) throws Exception {
		Map<Path, java.nio.file.attribute.FileTime> timestamps = new HashMap<>();
		Map<Path, byte[]> hashes = new HashMap<>();
		try (var files = Files.list(world.resolve("region"))) {
			for (Path file : files.toList()) {
				timestamps.put(file, Files.getLastModifiedTime(file));
				hashes.put(file, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
			}
		}
		var source = new WorldMapImporter.Source("test", "Read-only test", world, false);
		long start = System.nanoTime();
		var candidates = WorldMapImporter.scan(source);
		System.out.println("Map scan: " + candidates.size() + " candidates in " + (System.nanoTime() - start) / 1e9 + " seconds");
		for (var candidate : candidates) System.out.println(candidate);
		check(!candidates.isEmpty(), "No candidates in provided map");
		for (var candidate : candidates.subList(0, Math.min(2, candidates.size()))) {
			var loaded = WorldMapImporter.load(source, candidate.bounds());
			check(!loaded.blocks().isEmpty(), "Real candidate did not load");
			var capture = WorldMapImporter.capture(loaded, candidate.bounds());
			check(capture.size() == candidate.bounds().volume(), "Real crop truncated");
			System.out.println("Map candidate loaded: blocks=" + loaded.blocks().size() + " crop=" + capture.size());
		}
		for (var entry : timestamps.entrySet()) check(entry.getValue().equals(Files.getLastModifiedTime(entry.getKey())), "Source map modified");
		for (var entry : hashes.entrySet()) check(Arrays.equals(entry.getValue(), MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(entry.getKey()))), "Source map bytes modified");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
