package dev.grandbuilder.build;

import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldMapImporter {
	private static final Logger LOGGER = LoggerFactory.getLogger("grand_builder/world_import");
	private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
	private static final int MAX_SOURCES = 64, MAX_REGIONS = 96, MAX_CHUNKS = 4096;
	private static final int MAX_CANDIDATES = 20, MAX_EVIDENCE_CELLS = 120000;
	private static final int[] BUILDING_HEIGHT_CUTS = {4, 12, 24, 48, 96, 160};
	private static final long MAX_REGION_BYTES = 64L * 1024 * 1024, MAX_VOLUME = 1000000;
	private WorldMapImporter() { }

	public record Source(String id, String name, Path path, boolean zip) { }
	public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		public int width() { return maxX - minX + 1; }
		public int height() { return maxY - minY + 1; }
		public int depth() { return maxZ - minZ + 1; }
		public long volume() { return (long) width() * height() * depth(); }
		public boolean contains(BlockPos pos) {
			return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
				&& pos.getZ() >= minZ && pos.getZ() <= maxZ;
		}
		public Bounds expand(int horizontal, int vertical) {
			return new Bounds(minX-horizontal,minY-vertical,minZ-horizontal,maxX+horizontal,maxY+vertical,maxZ+horizontal);
		}
		public Bounds adjust(int face, int delta) {
			return switch (face) {
				case 0 -> new Bounds(minX+delta,minY,minZ,maxX,maxY,maxZ);
				case 1 -> new Bounds(minX,minY,minZ,maxX+delta,maxY,maxZ);
				case 2 -> new Bounds(minX,minY+delta,minZ,maxX,maxY,maxZ);
				case 3 -> new Bounds(minX,minY,minZ,maxX,maxY+delta,maxZ);
				case 4 -> new Bounds(minX,minY,minZ+delta,maxX,maxY,maxZ);
				case 5 -> new Bounds(minX,minY,minZ,maxX,maxY,maxZ+delta);
				default -> this;
			};
		}
		public boolean valid() { return width() > 0 && height() > 0 && depth() > 0 && volume() <= MAX_VOLUME; }
	}
	public record Candidate(Bounds bounds, int score, boolean partial) { }
	public record StoredBlock(BlockState state, CompoundTag nbt) { }
	public record Loaded(Bounds available, Map<Long, StoredBlock> blocks) {
		// Packed BlockPos keys need HashMap's spread function for fast crop lookups.
		public Loaded { blocks = Collections.unmodifiableMap(new HashMap<>(blocks)); }
		public StoredBlock at(BlockPos pos) { return blocks.get(pos.asLong()); }
	}
	private record RegionRef(int x, int z, Path path, String zipEntry) { }
	private record Section(int baseY, BlockState[] palette, long[] packed, int bits) {
		private Section {
			if (palette.length > 1 && packed.length != (4096 * bits + 63) / 64
				&& packed.length != (4096 + 64 / bits - 1) / (64 / bits)) throw new IllegalArgumentException("Invalid section palette data");
		}
		private int paletteIndex(int x, int y, int z) {
			if (palette.length == 0) return -1;
			if (palette.length == 1) return 0;
			int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
			int perLong = 64 / bits;
			// Before 1.16, palette entries could straddle two longs.
			boolean dense = packed.length == (4096 * bits + 63) / 64;
			int bitIndex = dense ? index * bits : index / perLong * 64 + index % perLong * bits;
			int longIndex = bitIndex / 64, shift = bitIndex % 64;
			if (longIndex >= packed.length) return -1;
			long value = packed[longIndex] >>> shift;
			if (shift + bits > 64) {
				if (longIndex + 1 >= packed.length) return -1;
				value |= packed[longIndex + 1] << (64 - shift);
			}
			int paletteIndex = (int) (value & ((1L << bits) - 1));
			return paletteIndex < palette.length ? paletteIndex : -1;
		}
		private BlockState at(int x, int y, int z) {
			int index=paletteIndex(x,y,z);
			return index < 0 ? Blocks.AIR.defaultBlockState() : palette[index];
		}
	}
	private static final class Evidence {
		int score, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		void add(int x, int y, int z) {
			score++; minX=Math.min(minX,x); minY=Math.min(minY,y); minZ=Math.min(minZ,z);
			maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); maxZ=Math.max(maxZ,z);
		}
		void merge(Evidence other) {
			score+=other.score; minX=Math.min(minX,other.minX); minY=Math.min(minY,other.minY);
			minZ=Math.min(minZ,other.minZ); maxX=Math.max(maxX,other.maxX);
			maxY=Math.max(maxY,other.maxY); maxZ=Math.max(maxZ,other.maxZ);
		}
	}

	public static Path importDirectory() {
		return FabricLoader.getInstance().getGameDir().resolve("grand_builder").resolve("world_imports");
	}
	public static List<Source> sources() {
		List<Source> sources = new ArrayList<>();
		collect(importDirectory(), "import", sources);
		collect(FabricLoader.getInstance().getGameDir().resolve("saves"), "save", sources);
		sources.sort(Comparator.comparing(Source::name, String.CASE_INSENSITIVE_ORDER));
		return sources.size() > MAX_SOURCES ? List.copyOf(sources.subList(0, MAX_SOURCES)) : List.copyOf(sources);
	}
	private static void collect(Path root, String category, List<Source> result) {
		try {
			Files.createDirectories(root);
			try (var paths = Files.list(root)) {
				for (Path path : paths.sorted().toList()) {
					if (result.size() >= MAX_SOURCES) break;
					if (Files.isSymbolicLink(path)) continue;
					Path world = path;
					if (Files.isDirectory(world) && !Files.isDirectory(world.resolve("region"))) {
						try (var children = Files.list(world)) {
							List<Path> nested = children.filter(child -> Files.isDirectory(child.resolve("region"))
								&& !Files.isSymbolicLink(child)).limit(2).toList();
							if (nested.size() == 1) world = nested.get(0);
						}
					}
					boolean zip = Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
					if (!zip && !Files.isDirectory(world.resolve("region"))) continue;
					String name = path.getFileName().toString();
					if (name.length()>245) continue;
					result.add(new Source(category + ":" + name, name, zip ? path : world, zip));
				}
			}
		} catch (IOException exception) {
			LOGGER.warn("Could not list imported worlds in {}", root, exception);
		}
	}

	public static List<Candidate> scan(Source source) throws IOException {
		Map<Long, Evidence> cells = new HashMap<>();
		visitChunks(source, null, chunk -> {
			for (Section section : sections(chunk)) {
				boolean[] interesting = new boolean[section.palette.length];
				boolean any = false;
				for (int i = 0; i < interesting.length; i++) {
					interesting[i] = isConstructionMaterial(section.palette[i]);
					any |= interesting[i];
				}
				if (!any) continue;
				int chunkX = chunk.getIntOr("xPos", 0), chunkZ = chunk.getIntOr("zPos", 0);
				for (int y = section.baseY; y < section.baseY + 16; y++) for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++)
					for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
						int paletteIndex=section.paletteIndex(x,y,z);
						if (paletteIndex<0 || !interesting[paletteIndex]) continue;
						long key = cellKey(Math.floorDiv(x,4), Math.floorDiv(z,4));
						if (cells.size() >= MAX_EVIDENCE_CELLS && !cells.containsKey(key)) continue;
						cells.computeIfAbsent(key, ignored -> new Evidence()).add(x,y,z);
					}
			}
		});
		List<Candidate> result = new ArrayList<>();
		for (List<Long> group : connectedCells(cells.keySet())) addCandidates(cells, group, result, 0, false);
		result.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparing(Candidate::partial)
			.thenComparingInt(candidate -> candidate.bounds().minX()).thenComparingInt(candidate -> candidate.bounds().minZ()));
		return List.copyOf(result.subList(0, Math.min(MAX_CANDIDATES, result.size())));
	}

	private static List<List<Long>> connectedCells(Set<Long> keys) {
		List<List<Long>> groups = new ArrayList<>();
		Set<Long> remaining = new HashSet<>(keys);
		for (long seed : keys.stream().sorted().toList()) {
			if (!remaining.remove(seed)) continue;
			ArrayDeque<Long> queue = new ArrayDeque<>();
			queue.add(seed);
			List<Long> group = new ArrayList<>();
			while (!queue.isEmpty()) {
				long current = queue.removeFirst();
				group.add(current);
				int gx = (int)(current >> 32), gz = (int)current;
				for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
					long next = cellKey(gx + dx, gz + dz);
					if (remaining.remove(next)) queue.add(next);
				}
			}
			groups.add(group);
		}
		return groups;
	}

	private static void addCandidates(Map<Long, Evidence> cells, List<Long> keys, List<Candidate> result, int cut, boolean partial) {
		Evidence group = new Evidence();
		for (long key : keys) group.merge(cells.get(key));
		if (group.score < 24 || group.maxX - group.minX < 3 || group.maxZ - group.minZ < 3) return;
		Bounds raw = new Bounds(group.minX, group.minY, group.minZ, group.maxX, group.maxY, group.maxZ);
		Bounds padded = raw.expand(2, 2);
		if (raw.valid()) {
			result.add(new Candidate(padded.valid() ? padded : raw, group.score, partial));
			return;
		}
		// A shared road/plaza is not a reason to discard the buildings above it.
		for (int i = cut; i < BUILDING_HEIGHT_CUTS.length; i++) {
			Set<Long> raised = new HashSet<>();
			for (long key : keys) {
				Evidence cell = cells.get(key);
				if (cell.maxY - cell.minY >= 4 && cell.maxY >= group.minY + BUILDING_HEIGHT_CUTS[i]) raised.add(key);
			}
			if (raised.isEmpty() || raised.size() == keys.size()) continue;
			int before = result.size();
			for (List<Long> building : connectedCells(raised)) addCandidates(cells, building, result, i + 1, partial);
			if (result.size() > before) return;
		}
		// Oversized individual buildings remain selectable as explicitly labelled sections.
		if (keys.size() < 2) return;
		boolean splitX = raw.width() >= raw.depth();
		List<Long> ordered = keys.stream().sorted(Comparator.<Long>comparingInt(key -> splitX ? (int)(key >> 32) : (int)(long)key)
			.thenComparingLong(Long::longValue)).toList();
		int middle = ordered.size() / 2;
		int coordinate = splitX ? (int)(ordered.get(middle) >> 32) : (int)(long)ordered.get(middle);
		while (middle > 0 && (splitX ? (int)(ordered.get(middle - 1) >> 32) : (int)(long)ordered.get(middle - 1)) == coordinate) middle--;
		if (middle == 0) {
			while (middle < ordered.size() && (splitX ? (int)(ordered.get(middle) >> 32) : (int)(long)ordered.get(middle)) == coordinate) middle++;
		}
		if (middle == ordered.size()) return;
		addCandidates(cells, ordered.subList(0, middle), result, BUILDING_HEIGHT_CUTS.length, true);
		addCandidates(cells, ordered.subList(middle, ordered.size()), result, BUILDING_HEIGHT_CUTS.length, true);
	}

	public static Loaded load(Source source, Bounds requested) throws IOException {
		Bounds expanded = requested.expand(16, 8);
		if (!expanded.valid()) expanded=requested.expand(4,4);
		if (!expanded.valid()) expanded=requested;
		final Bounds available=expanded;
		if (!available.valid()) throw new IOException("World candidate exceeds the import volume limit");
		Map<Long, StoredBlock> blocks = new HashMap<>();
		visitChunks(source, available, chunk -> {
			int chunkX = chunk.getIntOr("xPos", 0), chunkZ = chunk.getIntOr("zPos", 0);
			for (Section section : sections(chunk)) {
				int y0 = Math.max(section.baseY, available.minY()), y1 = Math.min(section.baseY+15, available.maxY());
				int x0 = Math.max(chunkX << 4, available.minX()), x1 = Math.min((chunkX << 4)+15, available.maxX());
				int z0 = Math.max(chunkZ << 4, available.minZ()), z1 = Math.min((chunkZ << 4)+15, available.maxZ());
				for (int y=y0;y<=y1;y++) for (int z=z0;z<=z1;z++) for (int x=x0;x<=x1;x++) {
					BlockState state = section.at(x,y,z);
					if (!state.isAir()) blocks.put(BlockPos.asLong(x,y,z), new StoredBlock(state,null));
				}
			}
			ListTag blockEntities = chunk.getListOrEmpty("block_entities");
			if (blockEntities.isEmpty()) blockEntities = chunk.getListOrEmpty("TileEntities");
			for (var blockEntity : blockEntities) {
				if (!(blockEntity instanceof CompoundTag tag)) continue;
				BlockPos pos = new BlockPos(tag.getIntOr("x",0),tag.getIntOr("y",0),tag.getIntOr("z",0));
				StoredBlock existing = blocks.get(pos.asLong());
				if (existing != null) blocks.put(pos.asLong(), new StoredBlock(existing.state(),tag.copy()));
			}
		});
		return new Loaded(available,blocks);
	}

	public static List<GrandPalaceBlueprint.RelativeBlock> capture(Loaded loaded, Bounds crop) throws IOException {
		if (!crop.valid() || !inside(crop, loaded.available())) throw new IOException("Invalid crop bounds");
		List<GrandPalaceBlueprint.RelativeBlock> result = new ArrayList<>((int)crop.volume());
		int centerX = crop.width()/2, centerZ = crop.depth()/2, solids=0;
		for (int y=crop.minY();y<=crop.maxY();y++) for (int z=crop.minZ();z<=crop.maxZ();z++)
			for (int x=crop.minX();x<=crop.maxX();x++) {
				StoredBlock stored = loaded.blocks().get(BlockPos.asLong(x,y,z));
				BlockState state = stored == null ? Blocks.AIR.defaultBlockState() : stored.state();
				if (!state.isAir()) solids++;
				int rx=x-crop.minX()-centerX, ry=y-crop.minY(), rz=z-crop.minZ()-centerZ;
				result.add(new GrandPalaceBlueprint.RelativeBlock(rx,ry,rz,state,ry*120+Math.floorMod(rx*5-rz*3,11),
					stored == null || stored.nbt() == null ? null : stored.nbt().copy()));
			}
		if (solids == 0) throw new IOException("The selected bounds contain no blocks");
		result.sort(Comparator.comparingInt(GrandPalaceBlueprint.RelativeBlock::stage));
		return result;
	}
	public static boolean inside(Bounds inner, Bounds outer) {
		return inner.minX()>=outer.minX() && inner.maxX()<=outer.maxX() && inner.minY()>=outer.minY()
			&& inner.maxY()<=outer.maxY() && inner.minZ()>=outer.minZ() && inner.maxZ()<=outer.maxZ();
	}
	private static long cellKey(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
	private static boolean isConstructionMaterial(BlockState state) {
		if (state.isAir()) return false;
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (id == null) return false;
		String name = id.getPath();
		if (!id.getNamespace().equals("minecraft")) return !name.contains("ore") && !name.contains("leaves");
		if (name.endsWith("_ore") || name.contains("leaves") || name.contains("_log") || name.contains("_wood")) return false;
		return name.contains("planks") || name.contains("bricks") || name.contains("glass") || name.contains("concrete")
			|| name.equals("iron_block") || name.equals("iron_bars") || name.equals("gold_block")
			|| name.equals("smooth_stone") || name.startsWith("smooth_sandstone") || name.equals("stone_bricks")
			|| name.contains("wool") || name.contains("terracotta") || name.contains("quartz") || name.contains("polished")
			|| name.contains("copper") || name.contains("prismarine") || name.contains("purpur") || name.contains("tile")
			|| name.contains("stairs") || name.contains("slab") || name.contains("door") || name.contains("fence")
			|| name.contains("trapdoor") || name.contains("lantern") || name.contains("chest") || name.contains("bookshelf")
			|| name.endsWith("_bed") || name.contains("carpet") || name.contains("crafting_table") || name.contains("furnace")
			|| name.contains("cobblestone") || name.contains("scaffolding") || name.contains("wall") && !name.contains("deepslate");
	}
	private static List<Section> sections(CompoundTag root) {
		List<Section> result = new ArrayList<>();
		ListTag list = root.getListOrEmpty("sections");
		if (list.isEmpty()) list = root.getListOrEmpty("Sections");
		for (int i=0;i<list.size();i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			CompoundTag states = entry.getCompoundOrEmpty("block_states");
			ListTag paletteTags = states.getListOrEmpty("palette");
			if (paletteTags.isEmpty()) { states=entry; paletteTags=entry.getListOrEmpty("Palette"); }
			if (paletteTags.isEmpty()) continue;
			BlockState[] palette = new BlockState[paletteTags.size()];
			for (int j=0;j<palette.length;j++) palette[j]=NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTags.getCompoundOrEmpty(j));
			int bits = Math.max(4, 32-Integer.numberOfLeadingZeros(Math.max(1,palette.length-1)));
			result.add(new Section(entry.getIntOr("Y",0)*16,palette,
				states.getLongArray("data").orElseGet(() -> entry.getLongArray("BlockStates").orElse(new long[0])),bits));
		}
		return result;
	}
	private static void visitChunks(Source source, Bounds filter, Consumer<CompoundTag> visitor) throws IOException {
		List<RegionRef> regions = regionRefs(source);
		int[] spawn = spawn(source);
		regions.sort(Comparator.comparingLong(region -> (long)(region.x()-Math.floorDiv(spawn[0],512))*(region.x()-Math.floorDiv(spawn[0],512))
			+ (long)(region.z()-Math.floorDiv(spawn[1],512))*(region.z()-Math.floorDiv(spawn[1],512))));
		int visited = 0, regionsVisited=0, readable = 0;
		Exception failure = null;
		for (RegionRef ref : regions) {
			if (filter==null && (regionsVisited>=MAX_REGIONS || visited>=MAX_CHUNKS)) break;
			if (filter != null && ((ref.x()<<9)>filter.maxX() || ((ref.x()+1)<<9)-1<filter.minX()
				|| (ref.z()<<9)>filter.maxZ() || ((ref.z()+1)<<9)-1<filter.minZ())) continue;
			regionsVisited++;
			Path temp = null, regionPath = ref.path();
			try {
				if (source.zip()) {
					temp = Files.createTempFile("grand-builder-map-", ".mca");
					try (ZipFile zip = new ZipFile(source.path().toFile())) {
						ZipEntry entry = zip.getEntry(ref.zipEntry());
						if (entry == null || entry.getSize() > MAX_REGION_BYTES) throw new IOException("Region file missing or too large");
						try (InputStream input = zip.getInputStream(entry); var output = Files.newOutputStream(temp)) {
							byte[] buffer = new byte[8192]; long bytes=0; int count;
							while ((count=input.read(buffer))>=0) {
								bytes+=count;
								if (bytes>MAX_REGION_BYTES) throw new IOException("Region file too large");
								output.write(buffer,0,count);
							}
						}
					}
					regionPath=temp;
				} else if (Files.size(regionPath)>MAX_REGION_BYTES) throw new IOException("Region file too large");
				try (FileChannel region = FileChannel.open(regionPath,StandardOpenOption.READ)) {
					ByteBuffer locations=ByteBuffer.allocate(4096);
					readFully(region,locations,0);
					locations.flip();
					for (int index=0;index<1024;index++) {
						int x=index & 31, z=index >> 5;
						int cx=ref.x()*32+x,cz=ref.z()*32+z;
						if (filter!=null && ((cx<<4)>filter.maxX() || ((cx+1)<<4)-1<filter.minX()
							|| (cz<<4)>filter.maxZ() || ((cz+1)<<4)-1<filter.minZ())) continue;
						int location=locations.getInt((x+z*32)*4);
						if (location==0) continue;
						if (visited>=MAX_CHUNKS) {
							if (filter!=null) throw new IOException("Selected crop exceeds the chunk read limit");
							break;
						}
						visited++;
						try {
							visitor.accept(readChunk(region,location,source,ref,cx,cz));
							readable++;
						} catch (Exception exception) {
							if (filter!=null) throw new IOException("Could not read selected chunk "+cx+","+cz,exception);
							failure=exception;
							LOGGER.debug("Skipping unreadable map chunk {},{}",cx,cz,exception);
						}
					}
				}
			} catch (Exception exception) {
				if (filter!=null) throw new IOException("Could not read selected region",exception);
				failure=exception;
				LOGGER.warn("Skipping unreadable map region {}",ref.path(),exception);
			} finally {
				if (temp!=null) Files.deleteIfExists(temp);
			}
		}
		if (readable==0 && failure!=null) throw new IOException("No readable chunks in the imported map",failure);
	}
	private static void readFully(FileChannel file, ByteBuffer buffer, long offset) throws IOException {
		while (buffer.hasRemaining()) {
			int read=file.read(buffer,offset);
			if (read<=0) throw new IOException("Truncated Anvil region");
			offset+=read;
		}
	}
	private static CompoundTag readChunk(FileChannel file, int location, Source source, RegionRef ref, int x, int z) throws IOException {
		// Anvil location entries contain a sector offset and sector count; the source stays read-only.
		long offset=(long)(location>>>8)*4096;
		int sectors=location & 255;
		if (offset<8192 || sectors==0 || offset+5>file.size()) throw new IOException("Invalid Anvil location");
		ByteBuffer header=ByteBuffer.allocate(5);
		readFully(file,header,offset); header.flip();
		int length=header.getInt(), compression=header.get() & 255;
		RegionFileVersion version=RegionFileVersion.fromId(compression & 127);
		if (version==null || version==RegionFileVersion.VERSION_CUSTOM) throw new IOException("Unsupported chunk compression");
		InputStream compressed;
		if ((compression & 128)!=0) {
			String name="c."+x+"."+z+".mcc";
			if (source.zip()) {
				try (ZipFile zip=new ZipFile(source.path().toFile())) {
					String entryName=ref.zipEntry().substring(0,ref.zipEntry().lastIndexOf('/')+1)+name;
					ZipEntry entry=zip.getEntry(entryName);
					if (entry==null || entry.getSize()>8L*1024*1024) throw new IOException("Missing external chunk");
					try (InputStream input=zip.getInputStream(entry)) {
						byte[] bytes=input.readNBytes(8*1024*1024+1);
						if (bytes.length>8*1024*1024) throw new IOException("External chunk too large");
						compressed=new ByteArrayInputStream(bytes);
					}
				}
			} else {
				Path external=ref.path().getParent().resolve(name);
				if (Files.isSymbolicLink(external) || Files.size(external)>8L*1024*1024) throw new IOException("External chunk too large");
				compressed=Files.newInputStream(external);
			}
		} else {
			if (length<=1 || length>sectors*4096-4 || offset+4+length>file.size()) throw new IOException("Invalid chunk length");
			ByteBuffer data=ByteBuffer.allocate(length-1);
			readFully(file,data,offset+5);
			compressed=new ByteArrayInputStream(data.array());
		}
		try (InputStream input=compressed; DataInputStream nbt=new DataInputStream(version.wrap(input))) {
			CompoundTag root=NbtIo.read(nbt,NbtAccounter.create(8L*1024*1024));
			return root.contains("xPos") ? root : root.getCompoundOrEmpty("Level");
		}
	}
	private static List<RegionRef> regionRefs(Source source) throws IOException {
		List<RegionRef> refs = new ArrayList<>();
		if (source.zip()) {
			try (ZipFile zip = new ZipFile(source.path().toFile())) {
				String root="";
				ZipEntry level=zipLevel(zip);
				if (level!=null) {
					String name=level.getName().replace('\\','/');
					root=name.substring(0,name.length()-"level.dat".length());
				}
				for (var entries = zip.entries();entries.hasMoreElements();) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName().replace('\\','/');
					if (!name.contains("/region/") && !name.startsWith("region/")) continue;
					if (name.startsWith("DIM-1/") || name.startsWith("DIM1/") || name.contains("/DIM-1/")
						|| name.contains("/DIM1/") || name.contains("dimensions/")) continue;
					if (level!=null && (!name.startsWith(root+"region/") || name.substring((root+"region/").length()).contains("/"))) continue;
					Matcher match = REGION_NAME.matcher(name.substring(name.lastIndexOf('/')+1));
					if (match.matches()) refs.add(new RegionRef(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),null,entry.getName()));
				}
			}
		} else {
			try (var paths=Files.list(source.path().resolve("region"))) {
				for (Path path: paths.toList()) {
					if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) continue;
					Matcher match=REGION_NAME.matcher(path.getFileName().toString());
					if (match.matches()) refs.add(new RegionRef(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),path,null));
				}
			}
		}
		return refs;
	}
	private static ZipEntry zipLevel(ZipFile zip) {
		ZipEntry level=null;
		for (var entries=zip.entries();entries.hasMoreElements();) {
			ZipEntry entry=entries.nextElement();
			String name=entry.getName().replace('\\','/');
			if ((name.equals("level.dat") || name.endsWith("/level.dat")) && (level==null || name.length()<level.getName().length())) level=entry;
		}
		return level;
	}
	private static int[] spawn(Source source) {
		try {
			CompoundTag root;
			if (source.zip()) {
				try (ZipFile zip = new ZipFile(source.path().toFile())) {
					ZipEntry level=zipLevel(zip);
					if (level==null || level.getSize()>8L*1024*1024) return new int[]{0,0};
					try (InputStream input=zip.getInputStream(level)) { root=NbtIo.readCompressed(input,NbtAccounter.create(8L*1024*1024)); }
				}
			} else root=NbtIo.readCompressed(source.path().resolve("level.dat"),NbtAccounter.create(8L*1024*1024));
			CompoundTag data=root.getCompoundOrEmpty("Data");
			var modern=data.read("spawn",net.minecraft.world.level.storage.LevelData.RespawnData.CODEC);
			if (modern.isPresent()) return new int[]{modern.get().pos().getX(),modern.get().pos().getZ()};
			return new int[]{data.getIntOr("SpawnX",0),data.getIntOr("SpawnZ",0)};
		} catch (Exception ignored) { return new int[]{0,0}; }
	}
}
