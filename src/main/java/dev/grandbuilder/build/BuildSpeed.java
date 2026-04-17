package dev.grandbuilder.build;

import dev.grandbuilder.config.GrandBuilderConfig;
import java.util.Locale;

public enum BuildSpeed {
	ULTRA_SLOW("ultra_slow", 1, 8),
	GLACIAL("glacial", 1, 4),
	VERY_SLOW("very_slow", 1, 1),
	SLOW("slow", 2, 1),
	NORMAL("normal", 6, 1),
	FAST("fast", 14, 1),
	TURBO("turbo", 28, 1),
	HYPER("hyper", 64, 1),
	HIGHER("higher", 96, 1),
	MUCH_HIGHER("much_higher", 160, 1),
	EXTREME("extreme", 256, 1),
	OVERDRIVE("overdrive", 512, 1);

	private final String configKey;
	private final int defaultBlocksPerCycle;
	private final int defaultTickDelay;

	BuildSpeed(String configKey, int defaultBlocksPerCycle, int defaultTickDelay) {
		this.configKey = configKey;
		this.defaultBlocksPerCycle = defaultBlocksPerCycle;
		this.defaultTickDelay = defaultTickDelay;
	}

	public int blocksPerCycle() {
		return profile().blocksPerCycle;
	}

	public int tickDelay() {
		return profile().tickDelay;
	}

	public double effectiveBlocksPerTick() {
		return blocksPerCycle() / (double) tickDelay();
	}

	public String displayRate() {
		double rate = effectiveBlocksPerTick();
		if (Math.abs(rate - Math.rint(rate)) < 0.0001) {
			return Integer.toString((int) Math.round(rate));
		}
		return String.format(Locale.US, "%.2f", rate);
	}

	public String translationKey() {
		return "speed.grand_builder." + configKey;
	}

	public String configKey() {
		return configKey;
	}

	public int defaultBlocksPerCycle() {
		return defaultBlocksPerCycle;
	}

	public int defaultTickDelay() {
		return defaultTickDelay;
	}

	public BuildSpeed next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public BuildSpeed previous() {
		return values()[(ordinal() - 1 + values().length) % values().length];
	}

	public int networkId() {
		return ordinal();
	}

	public static BuildSpeed byNetworkId(int id) {
		BuildSpeed[] values = values();
		if (id < 0 || id >= values.length) {
			return NORMAL;
		}
		return values[id];
	}

	private GrandBuilderConfig.SpeedProfile profile() {
		return GrandBuilderConfig.get().speedProfile(this);
	}
}
