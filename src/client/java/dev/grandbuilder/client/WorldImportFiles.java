package dev.grandbuilder.client;

import dev.grandbuilder.build.WorldMapImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class WorldImportFiles {
	private WorldImportFiles() { }
	static void stage(List<Path> paths) throws IOException {
		Path directory=WorldMapImporter.importDirectory();
		Files.createDirectories(directory);
		for (Path source:paths) {
			if (Files.isSymbolicLink(source)) throw new IOException("Symbolic link is not a world");
			String name=source.getFileName().toString();
			if (Files.isRegularFile(source) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
				Files.copy(source,unique(directory,name));
				continue;
			}
			Path world=source;
			if (Files.isDirectory(world) && !Files.isDirectory(world.resolve("region"))) {
				try (var children=Files.list(world)) {
					List<Path> nested=children.filter(child -> Files.isDirectory(child.resolve("region")) && !Files.isSymbolicLink(child)).limit(2).toList();
					if (nested.size()==1) world=nested.getFirst();
				}
			}
			if (!Files.isDirectory(world.resolve("region")) || Files.isSymbolicLink(world.resolve("region"))) throw new IOException("Not a Java world");
			Path target=unique(directory,name);
			Files.createDirectory(target);Files.createDirectory(target.resolve("region"));
			Path level=world.resolve("level.dat");
			if (Files.isRegularFile(level) && !Files.isSymbolicLink(level)) Files.copy(level,target.resolve("level.dat"));
			try (var files=Files.list(world.resolve("region"))) {
				for (Path file:files.filter(Files::isRegularFile).toList()) {
					String fileName=file.getFileName().toString();
					if (!Files.isSymbolicLink(file) && (fileName.endsWith(".mca") || fileName.endsWith(".mcc")))
						Files.copy(file,target.resolve("region").resolve(fileName));
				}
			}
		}
	}
	private static Path unique(Path directory,String name) {
		Path target=directory.resolve(name);
		for (int suffix=2;Files.exists(target);suffix++) {
			int dot=name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.length()-4:name.length();
			target=directory.resolve(name.substring(0,dot)+"_"+suffix+name.substring(dot));
		}
		return target;
	}
}
