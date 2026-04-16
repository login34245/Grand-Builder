package dev.grandbuilder.network;

import dev.grandbuilder.GrandBuilderMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StructureListPayload(List<Entry> entries) implements CustomPacketPayload {
	private static final int MAX_ENTRIES = 512;
	private static final int MAX_KEY_LENGTH = 256;
	private static final int MAX_NAME_LENGTH = 256;
	private static final int MAX_TRANSLATION_KEY_LENGTH = 256;

	public static final Type<StructureListPayload> TYPE = new Type<>(GrandBuilderMod.id("structure_list"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StructureListPayload> CODEC = StreamCodec.of(
		StructureListPayload::encode,
		StructureListPayload::decode
	);

	private static void encode(RegistryFriendlyByteBuf buffer, StructureListPayload payload) {
		int count = Math.min(payload.entries().size(), MAX_ENTRIES);
		buffer.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			Entry entry = payload.entries().get(i);
			buffer.writeUtf(entry.key(), MAX_KEY_LENGTH);
			buffer.writeUtf(entry.displayName(), MAX_NAME_LENGTH);
			buffer.writeUtf(entry.translationKey(), MAX_TRANSLATION_KEY_LENGTH);
			buffer.writeBoolean(entry.external());
		}
	}

	private static StructureListPayload decode(RegistryFriendlyByteBuf buffer) {
		int count = Math.min(buffer.readVarInt(), MAX_ENTRIES);
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(
				buffer.readUtf(MAX_KEY_LENGTH),
				buffer.readUtf(MAX_NAME_LENGTH),
				buffer.readUtf(MAX_TRANSLATION_KEY_LENGTH),
				buffer.readBoolean()
			));
		}
		return new StructureListPayload(List.copyOf(entries));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public record Entry(String key, String displayName, String translationKey, boolean external) {
	}
}
