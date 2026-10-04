package dev.grandbuilder.build;

import dev.grandbuilder.config.GrandBuilderConfig;
import dev.grandbuilder.network.FilmingAction;
import dev.grandbuilder.network.FilmingStatePayload;
import dev.grandbuilder.network.FilmingToolsPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class FilmingToolsManager {
	private static final int WEATHER_DURATION = 24000;
	private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
	private static final Map<UUID, Long> LAST_STATUS = new HashMap<>();

	private FilmingToolsManager() { }

	public static void handle(ServerPlayer player, FilmingToolsPayload payload) {
		FilmingAction action = FilmingAction.byId(payload.actionId());
		if (action == null) return;
		ServerLevel level = (ServerLevel) player.level();
		GrandBuilderConfig config = GrandBuilderConfig.get();
		boolean permitted = config.hasPermission(player) && config.isDimensionAllowed(level.dimension())
			&& player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		long now = player.level().getServer().getTickCount();
		if (action == FilmingAction.STATUS) {
			if (now - LAST_STATUS.getOrDefault(player.getUUID(), -100L) < 10) return;
			LAST_STATUS.put(player.getUUID(), now);
			sendState(player, permitted, FilmingStatePayload.STATUS);
			return;
		}
		if (!permitted) { sendState(player, false, FilmingStatePayload.DENIED); return; }
		if (now - LAST_ACTION.getOrDefault(player.getUUID(), -100L) < 3) {
			sendState(player, true, FilmingStatePayload.COOLDOWN); return;
		}
		LAST_ACTION.put(player.getUUID(), now);
		if (action.changesTime()) AnimatedBuildManager.setFilmingDayTime(level, action.dayTime());
		else switch (action) {
			case INVISIBILITY_ON -> player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
				MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			case INVISIBILITY_OFF -> player.removeEffect(MobEffects.INVISIBILITY);
			case CLEAR -> level.setWeatherParameters(WEATHER_DURATION, 0, false, false);
			case RAIN -> level.setWeatherParameters(0, WEATHER_DURATION, true, false);
			case THUNDER -> level.setWeatherParameters(0, WEATHER_DURATION, true, true);
			default -> { }
		}
		sendState(player, true, FilmingStatePayload.APPLIED);
	}

	private static void sendState(ServerPlayer player, boolean permitted, int result) {
		ServerLevel level = (ServerLevel) player.level();
		ServerPlayNetworking.send(player, new FilmingStatePayload(permitted, player.hasEffect(MobEffects.INVISIBILITY),
			(int) Math.floorMod(level.getDayTime(), 24000), level.getLevelData().isThundering() ? 2 : level.getLevelData().isRaining() ? 1 : 0, result));
	}

	public static void disconnect(UUID player) { LAST_ACTION.remove(player); LAST_STATUS.remove(player); }
	public static void clear() { LAST_ACTION.clear(); LAST_STATUS.clear(); }
}
