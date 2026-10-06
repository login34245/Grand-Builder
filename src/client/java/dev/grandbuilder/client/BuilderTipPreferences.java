package dev.grandbuilder.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.grandbuilder.build.BuildSpeed;
import dev.grandbuilder.build.BuildEffectMode;
import dev.grandbuilder.build.PlacementPolicy;
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
		BuilderTheme theme = BuilderTheme.CURRENT;
		boolean structureList;
		boolean effectList;
		boolean cinematicCamera = true;
		BuildSpeed defaultSpeed = BuildSpeed.NORMAL;
		boolean defaultTerrain;
		BuildEffectMode defaultEffect = BuildEffectMode.STANDARD;
		PlacementPolicy defaultPlacement = PlacementPolicy.PRESERVE;
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
		if (settings.theme == null) settings.theme = BuilderTheme.CURRENT;
		if (settings.defaultSpeed == null) settings.defaultSpeed = BuildSpeed.NORMAL;
		if (settings.defaultEffect == null) settings.defaultEffect = BuildEffectMode.STANDARD;
		if (settings.defaultPlacement == null) settings.defaultPlacement = PlacementPolicy.PRESERVE;
	}

	public BuilderTheme theme() { return settings.theme; }
	public boolean structureList() { return settings.structureList; }
	public boolean effectList() { return settings.effectList; }
	public void setEffectList(boolean value) { settings.effectList = value; save(); }
	public boolean cinematicCamera() { return settings.cinematicCamera; }
	public void setCinematicCamera(boolean value) { settings.cinematicCamera = value; save(); }
	public BuildSpeed defaultSpeed() { return settings.defaultSpeed; }
	public boolean defaultTerrain() { return settings.defaultTerrain; }
	public BuildEffectMode defaultEffect() { return settings.defaultEffect; }
	public PlacementPolicy defaultPlacement() { return settings.defaultPlacement; }
	public void setTheme(BuilderTheme value) { settings.theme = java.util.Objects.requireNonNull(value); save(); }
	public void setStructureList(boolean value) { settings.structureList = value; save(); }
	public void setDefaultSpeed(BuildSpeed value) { settings.defaultSpeed = java.util.Objects.requireNonNull(value); save(); }
	public void setDefaultTerrain(boolean value) { settings.defaultTerrain = value; save(); }
	public void setDefaultEffect(BuildEffectMode value) { settings.defaultEffect = java.util.Objects.requireNonNull(value); save(); }
	public void setDefaultPlacement(PlacementPolicy value) { settings.defaultPlacement = java.util.Objects.requireNonNull(value); save(); }
	public void resetDefaults() {
		Settings defaults = new Settings();
		defaults.lastShownAtMillis = settings.lastShownAtMillis;
		defaults.nextTip = settings.nextTip;
		settings = defaults;
		save();
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
