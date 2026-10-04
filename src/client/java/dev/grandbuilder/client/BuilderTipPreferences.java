package dev.grandbuilder.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuilderTipPreferences {
	static final long COOLDOWN_MILLIS = 15 * 60 * 1000L;
	static final int TIP_COUNT = 9;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("grand_builder/tips");
	private static BuilderTipPreferences current;
	private final Path path;
	private Settings settings = new Settings();

	private static final class Settings {
		boolean showTips = true;
		long lastShownAtMillis;
		int nextTip;
	}

	public static BuilderTipPreferences get() {
		if (current == null) current = new BuilderTipPreferences(FabricLoader.getInstance().getConfigDir().resolve("grand_builder_client.json"));
		return current;
	}

	BuilderTipPreferences(Path path) {
		this.path = path.toAbsolutePath();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				Settings loaded = GSON.fromJson(reader, Settings.class);
				if (loaded != null) settings = loaded;
			} catch (IOException | RuntimeException exception) {
				LOGGER.warn("Cannot read builder tip preferences {}, using defaults", path, exception);
			}
		}
		settings.lastShownAtMillis = Math.max(0, settings.lastShownAtMillis);
		settings.nextTip = Math.floorMod(settings.nextTip, TIP_COUNT);
	}

	public boolean enabled() { return settings.showTips; }

	public void setEnabled(boolean enabled) {
		if (settings.showTips == enabled) return;
		settings.showTips = enabled;
		save();
	}

	int takeAutomaticTip(long now) {
		if (!enabled() || now <= 0) return -1;
		// A backwards clock adjustment must not bypass the saved cooldown.
		if (settings.lastShownAtMillis > 0 && (now < settings.lastShownAtMillis
			|| now - settings.lastShownAtMillis < COOLDOWN_MILLIS)) return -1;
		settings.lastShownAtMillis = now;
		return takeNextTip();
	}

	int takeNextTip() {
		int tip = settings.nextTip;
		settings.nextTip = (tip + 1) % TIP_COUNT;
		save();
		return tip;
	}

	private void save() {
		Path temporary = null;
		try {
			Files.createDirectories(path.getParent());
			temporary = Files.createTempFile(path.getParent(), "grand-builder-client-", ".tmp");
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(settings, writer); }
			try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException exception) {
			LOGGER.warn("Cannot save builder tip preferences {}", path, exception);
		} finally {
			if (temporary != null) try { Files.deleteIfExists(temporary); }
			catch (IOException exception) { LOGGER.warn("Cannot remove temporary tip preferences {}", temporary, exception); }
		}
	}
}
