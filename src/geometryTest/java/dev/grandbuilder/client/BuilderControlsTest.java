package dev.grandbuilder.client;

import com.google.gson.JsonParser;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.build.PreviewPlacement;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;

public final class BuilderControlsTest {
	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		verifyMovement();
		verifyCodec();
		verifyTranslations();
		Path temporary = Files.createTempDirectory("grand-builder-tips-test-");
		try { verifyPreferences(temporary); }
		finally {
			try (var paths = Files.walk(temporary)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		System.out.println("Builder controls verified: 1/10-block moves, network codec, persisted 15-minute cooldown, opt-out and EN/RU tips.");
	}

	private static void verifyMovement() {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			PreviewPlacement original = new PreviewPlacement(new BlockPos(-7, 100, 3), facing, new BlockPos(2, 100, 5));
			for (Direction direction : Direction.values()) for (int step : new int[] {1, 10}) {
				PreviewPlacement moved = original.move(direction, step);
				check(moved.origin().equals(original.origin().relative(direction, step)), "Incorrect movement distance");
				check(moved.pivot().equals(original.pivot().relative(direction, step)), "Pivot did not follow movement");
				check(moved.facing() == facing, "Movement rotated the structure");
				check(moved.move(direction.getOpposite(), step).equals(original), "Fast movement is not reversible");
				check(moved.rotate().equals(original.rotate().move(direction, step)), "Fast move changes the rotation pivot");
			}
		}
	}

	private static void verifyCodec() {
		for (BuildControlAction action : BuildControlAction.values()) for (boolean fast : new boolean[] {false, true}) {
			BuildControlPayload request = new BuildControlPayload(action.networkId(), fast);
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			try {
				BuildControlPayload.CODEC.encode(buffer, request);
				check(buffer.readableBytes() == 2, "Fast movement must remain one small packet");
				check(BuildControlPayload.CODEC.decode(buffer).equals(request), "Movement flag lost in network codec");
				check(buffer.readableBytes() == 0, "Unexpected trailing packet bytes");
			} finally { buffer.release(); }
			check(!new BuildControlPayload(action.networkId()).fastMovement(), "Existing commands must use the precise step");
		}
		check(BuildControlAction.ROTATE_PREVIEW.networkId() == 11 && BuildControlAction.MOVE_PREVIEW_DOWN.networkId() == 17,
			"Existing control IDs changed");
	}

	private static void verifyTranslations() throws Exception {
		Path folder = Path.of("src/main/resources/assets/grand_builder/lang");
		var english = JsonParser.parseString(Files.readString(folder.resolve("en_us.json"))).getAsJsonObject();
		var russian = JsonParser.parseString(Files.readString(folder.resolve("ru_ru.json"))).getAsJsonObject();
		check(english.keySet().equals(russian.keySet()), "EN/RU translation keys differ");
		for (int tip = 0; tip < BuilderTipPreferences.TIP_COUNT; tip++) {
			String key = "screen.grand_builder.tips.tip" + tip;
			check(english.has(key) && !english.get(key).getAsString().isBlank(), "Missing English tip");
			check(russian.has(key) && !russian.get(key).getAsString().isBlank(), "Missing Russian tip");
		}
	}

	private static void verifyPreferences(Path temporary) throws Exception {
		Path file = temporary.resolve("nested/client.json");
		BuilderTipPreferences preferences = new BuilderTipPreferences(file);
		long now = 1_800_000_000_000L, cooldown = BuilderTipPreferences.COOLDOWN_MILLIS;
		check(cooldown == 900_000, "Cooldown must be 15 real minutes");
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == 0, "First open must show the movement tip");
		check(preferences.takeAutomaticTip(now) == -1, "Immediate reopen repeated the popup");
		check(preferences.takeAutomaticTip(now + cooldown - 1) == -1, "Cooldown expires too early");
		preferences = new BuilderTipPreferences(file);
		check(preferences.takeAutomaticTip(now + cooldown - 1) == -1, "Restart bypassed the cooldown");
		check(preferences.takeAutomaticTip(now - 1000) == -1, "Backwards clock bypassed the cooldown");
		check(preferences.takeAutomaticTip(now + cooldown) == 1, "Cooldown boundary or saved tip sequence is wrong");
		preferences.setEnabled(false);
		preferences = new BuilderTipPreferences(file);
		check(!preferences.enabled() && preferences.takeAutomaticTip(now + 100 * cooldown) == -1, "Opt-out not persisted");
		preferences.setEnabled(true);
		check(preferences.takeAutomaticTip(now + cooldown + 1) == -1, "Re-enabling bypassed the cooldown");
		check(preferences.takeNextTip() == 2, "Manual next tip sequence is wrong");
		check(preferences.takeAutomaticTip(now + 2 * cooldown - 1) == -1, "Manual next tip cleared the cooldown");
		check(preferences.takeAutomaticTip(now + 2 * cooldown) == 3, "Manual next tip delayed the next automatic popup");
		for (int i = 0; i < 2 * BuilderTipPreferences.TIP_COUNT; i++)
			check(preferences.takeNextTip() == (i + 4) % BuilderTipPreferences.TIP_COUNT, "Tip cycle is not bounded");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now + 2 * cooldown + 1) == -1, "Final save did not retain settings");
		try (var files = Files.list(file.getParent())) { check(files.count() == 1, "Atomic save leaked temporary files"); }
		Files.writeString(file, "{\"nextTip\":-1,\"lastShownAtMillis\":-5}");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == BuilderTipPreferences.TIP_COUNT - 1, "Missing fields or invalid indices not normalized");
		Files.writeString(file, "null");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == 0, "Null preferences did not recover");
		Files.writeString(file, "broken json");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == 0, "Malformed preferences did not recover");
	}

	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
