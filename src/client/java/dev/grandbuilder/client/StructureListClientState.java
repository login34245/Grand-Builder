package dev.grandbuilder.client;

import dev.grandbuilder.build.StructureLibrary;
import dev.grandbuilder.network.StructureListPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class StructureListClientState {
	private static volatile List<StructureLibrary.SelectionEntry> entries = StructureLibrary.listBuiltinSelections();
	private static volatile int revision;

	private StructureListClientState() {
	}

	public static void update(StructureListPayload payload) {
		List<StructureLibrary.SelectionEntry> updated = new ArrayList<>();
		for (StructureListPayload.Entry entry : payload.entries()) {
			Component displayName = entry.external()
				? Component.translatable("structure.grand_builder.file", entry.displayName())
				: builtinDisplayName(entry);
			updated.add(new StructureLibrary.SelectionEntry(entry.key(), displayName));
		}

		if (updated.isEmpty()) {
			updated = StructureLibrary.listBuiltinSelections();
		}

		entries = List.copyOf(updated);
		revision++;
	}

	public static List<StructureLibrary.SelectionEntry> entries() {
		return entries;
	}

	public static int revision() {
		return revision;
	}

	public static void reset() {
		entries = StructureLibrary.listBuiltinSelections();
		revision++;
	}

	private static Component builtinDisplayName(StructureListPayload.Entry entry) {
		if (!entry.translationKey().isBlank()) {
			return Component.translatable(entry.translationKey());
		}
		if (!entry.displayName().isBlank()) {
			return Component.literal(entry.displayName());
		}
		return Component.literal(entry.key());
	}
}
