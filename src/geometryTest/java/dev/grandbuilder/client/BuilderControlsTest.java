package dev.grandbuilder.client;

import com.google.gson.JsonParser;
import dev.grandbuilder.network.BuildControlAction;
import dev.grandbuilder.network.BuildControlPayload;
import dev.grandbuilder.build.PreviewPlacement;
import dev.grandbuilder.build.SiteClearVolume;
import dev.grandbuilder.build.SiteClearTask;
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
		verifyShortPresses();
		verifyClearVolume();
		verifyClearTask();
		verifyCodec();
		verifyLargePreviewCodec();
		verifyFilming();
		verifyWorldHeight();
		verifySettingsLayout();
		verifyDefaultsCodec();
		verifyStatusRevision();
		verifyTranslations();
		Path temporary = Files.createTempDirectory("grand-builder-tips-test-");
		try { verifyPreferences(temporary); }
		finally {
			try (var paths = Files.walk(temporary)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		System.out.println("Builder controls verified: settings migration/persistence, layout/tabs, actual world heights, fresh status/defaults codecs, short/rebound presses, clearing/undo, 1/10-block moves, persisted 15-minute cooldown and EN/RU tips.");
	}
	private static void verifyStatusRevision() {
		int previous = BuildStatusClientState.revision();
		var payload = new dev.grandbuilder.network.BuildStatusPayload(0, "", 0, 0, 0, false, 11, 512, false);
		BuildStatusClientState.update(payload);
		check(BuildStatusClientState.revision() != previous && !BuildStatusClientState.snapshot().terrainAdaptationEnabled()
			&& BuildStatusClientState.snapshot().speedId() == 11, "Fresh idle settings were not acknowledged");
		previous = BuildStatusClientState.revision(); BuildStatusClientState.reset();
		check(BuildStatusClientState.revision() != previous, "Disconnect did not invalidate old status");
	}
	private static void verifySettingsLayout() {
		for (int[] size : new int[][] {{640,360},{427,240},{320,180},{1920,1080}}) {
			var layout = BuilderSettingsLayout.at(size[0], size[1]);
			check(layout.left() >= 0 && layout.top() >= 0 && layout.left() + layout.width() <= size[0]
				&& layout.top() + layout.height() <= size[1], "Settings panel left screen");
			check(layout.rowHeight() >= 11, "Settings buttons cannot contain the font");
			for (int row = 0; row < BuilderSettingsLayout.ROWS; row++) check(layout.rowY(row) + layout.rowHeight() <
				(row == BuilderSettingsLayout.ROWS - 1 ? layout.footerY() : layout.rowY(row + 1)), "Settings rows or footer overlap");
			check(layout.footerY() + 20 < layout.top() + layout.height(), "Settings footer left panel");
		}
	}
	private static void verifyWorldHeight() {
		for (int[] range : new int[][] {{-64, 384}, {-1024, 3056}, {0, 2048}, {-512, 1536}}) {
			var world = net.minecraft.world.level.LevelHeightAccessor.create(range[0], range[1]);
			int min = world.getMinY(), max = world.getMaxY() - 1;
			check(dev.grandbuilder.build.WorldHeightBounds.contains(world, min, max), "Actual world bounds were rejected");
			check(!dev.grandbuilder.build.WorldHeightBounds.contains(world, min - 1, max), "Below-world position was accepted");
			check(!dev.grandbuilder.build.WorldHeightBounds.contains(world, min, max + 1), "Exclusive ceiling became inclusive");
			check(!dev.grandbuilder.build.WorldHeightBounds.contains(world, max, min), "Inverted height range was accepted");
		}
		check(dev.grandbuilder.build.WorldHeightBounds.contains(net.minecraft.world.level.LevelHeightAccessor.create(-64, 2096), 0, 1610),
			"Tall building failed an expanded dimension");
		check(!dev.grandbuilder.build.WorldHeightBounds.contains(net.minecraft.world.level.LevelHeightAccessor.create(-64, 384), 0, 1610),
			"Tall building escaped vanilla dimension bounds");
	}
	private static void verifyDefaultsCodec() {
		for (var speed : dev.grandbuilder.build.BuildSpeed.values()) for (boolean terrain : new boolean[] {false, true}) {
			var payload = new dev.grandbuilder.network.BuildDefaultsPayload(speed.networkId(), terrain);
			var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			try {
				dev.grandbuilder.network.BuildDefaultsPayload.CODEC.encode(buffer, payload);
				check(dev.grandbuilder.network.BuildDefaultsPayload.CODEC.decode(buffer).equals(payload) && !buffer.isReadable(), "Defaults codec changed values");
			} finally { buffer.release(); }
		}
	}

	private static void verifyLargePreviewCodec() {
		var warning = new dev.grandbuilder.network.LargePreviewWarningPayload(Long.MIN_VALUE + 137, 1, "Large schematic", 1_000_000_000, 700_000_000, 1_073_741_824L);
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			dev.grandbuilder.network.LargePreviewWarningPayload.CODEC.encode(buffer, warning);
			check(dev.grandbuilder.network.LargePreviewWarningPayload.CODEC.decode(buffer).equals(warning) && !buffer.isReadable(), "Large warning codec changed counts/token");
		} finally { buffer.release(); }
		for (boolean accepted : new boolean[] {false, true}) {
			var confirm = new dev.grandbuilder.network.LargePreviewConfirmPayload(warning.token(), accepted);
			buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			try {
				dev.grandbuilder.network.LargePreviewConfirmPayload.CODEC.encode(buffer, confirm);
				check(dev.grandbuilder.network.LargePreviewConfirmPayload.CODEC.decode(buffer).equals(confirm) && !buffer.isReadable(), "Large confirmation codec changed");
			} finally { buffer.release(); }
		}
	}
	private static void verifyShortPresses() {
		var binding = new net.minecraft.client.KeyMapping("qa.grand_builder.vertical", org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP,
			net.minecraft.client.KeyMapping.Category.MISC);
		check(binding.matches(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP,0,0)),"Default vertical key does not match");
		binding.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_K));
		check(binding.matches(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_K,0,1))
			&& !binding.matches(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP,0,0)),"Rebound preview key or modifier was ignored");
		PreviewKeyState state = new PreviewKeyState();
		state.press();
		check(state.tick(true, false, false, true), "Press/release between ticks was lost");
		check(!state.tick(true, false, false, true), "Short press repeated");
		state.press();
		check(state.tick(true, true, true, true), "Press with vanilla click was lost");
		for (int tick=2; tick<=16; tick++)
			check(state.tick(true,true,false,true)==(tick==12 || tick==16), "Repeat or initial press doubled");
		state.tick(true,false,false,true);
		check(state.tick(true,true,false,true), "Rising edge without click was lost");
		state.press();
		check(!state.tick(false,true,true,true), "Inactive preview sent a movement");
		check(!state.tick(true,false,false,true), "Press leaked out of a menu");
	}
	private static void verifyClearVolume() {
		SiteClearVolume volume = new SiteClearVolume(new BlockPos(-2,64,7), new BlockPos(3,67,9));
		var positions = new java.util.HashSet<BlockPos>();
		for (long i=0; i<volume.size(); i++) {
			BlockPos p=volume.position(i);
			check(p.getX()>=-2 && p.getX()<=3 && p.getY()>=64 && p.getY()<=67 && p.getZ()>=7 && p.getZ()<=9, "Clear left its bounds");
			check(positions.add(p), "Clear revisited a cell");
			if (i>0) check(p.getY()<=volume.position(i-1).getY(), "Clear must run top-down");
		}
		check(positions.size()==72, "Clear missed empty/non-schematic positions");
		check(volume.estimateTicks(4097)==2 && volume.estimateTicks(0)==0, "Clear ETA is incorrect");
		SiteClearVolume huge=new SiteClearVolume(BlockPos.ZERO,new BlockPos(10000,319,10000));
		check(huge.size()>Integer.MAX_VALUE && huge.position(huge.size()-1).equals(new BlockPos(10000,0,10000)), "Clear cursor overflowed");
	}

	private static void verifyClearTask() {
		SiteClearVolume volume=new SiteClearVolume(BlockPos.ZERO,new BlockPos(3,2,3));
		var world=new java.util.HashMap<BlockPos,String>();
		var undo=new java.util.HashMap<BlockPos,String>();
		for (long i=0;i<volume.size();i++) if (i%3==0) world.put(volume.position(i),i%2==0?"water":"chest:diamonds=7");
		BlockPos outside=new BlockPos(4,1,2);world.put(outside,"untouched");
		var original=new java.util.HashMap<>(world);
		SiteClearTask task=new SiteClearTask(volume);
		var inspect=(java.util.function.Function<BlockPos,SiteClearTask.Cell>)pos -> world.containsKey(pos)?SiteClearTask.Cell.OCCUPIED:SiteClearTask.Cell.EMPTY;
		var remove=(java.util.function.Predicate<BlockPos>)pos -> {undo.putIfAbsent(pos,world.get(pos));world.remove(pos);return true;};
		check(task.step(1,100,Long.MAX_VALUE,inspect,remove)==SiteClearTask.Status.WORKING && world.equals(original),"Preflight changed the world");
		check(task.step(1,100,Long.MAX_VALUE,pos -> SiteClearTask.Cell.UNLOADED,remove)==SiteClearTask.Status.WAITING && world.equals(original),"Unloaded chunks were modified");
		int ticks=0;
		while (!task.done()) {
			int before=world.size();task.step(1,100,Long.MAX_VALUE,inspect,remove);
			check(before-world.size()<=1,"Clear exceeded the mutation budget");
			check(++ticks<100,"Clear did not finish");
		}
		check(world.size()==1 && "untouched".equals(world.get(outside)),"Clear crossed the boundary or missed cells");
		check(task.estimateTicks()==0,"Completed clear still has an ETA");
		world.putAll(undo);check(world.equals(original),"Undo lost blocks or container contents");
		SiteClearTask tooLarge=new SiteClearTask(volume);
		check(tooLarge.step(512,2,Long.MAX_VALUE,inspect,remove)==SiteClearTask.Status.LIMIT && world.equals(original),"Oversized clear deleted blocks before refusal");
		check(tooLarge.step(512,100,Long.MAX_VALUE,inspect,remove)==SiteClearTask.Status.LIMIT,"Refused clear could resume destructively");
		SiteClearTask empty=new SiteClearTask(new SiteClearVolume(BlockPos.ZERO,new BlockPos(99,19,99)));
		long scans=empty.remainingScans();
		empty.step(512,100,Long.MAX_VALUE,pos->SiteClearTask.Cell.EMPTY,pos->{throw new AssertionError("Air must not create snapshots");});
		check(scans-empty.remainingScans()==4096,"Clear scans are not bounded per tick");
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
		for (var action : dev.grandbuilder.network.FilmingAction.values()) {
			if (action != dev.grandbuilder.network.FilmingAction.STATUS)
				check(english.has(action.translationKey()) && russian.has(action.translationKey()), "Missing filming action label");
		}
		for (int tip = 0; tip < BuilderTipPreferences.TIP_COUNT; tip++) {
			String key = "screen.grand_builder.tips.tip" + tip;
			check(english.has(key) && !english.get(key).getAsString().isBlank(), "Missing English tip");
			check(russian.has(key) && !russian.get(key).getAsString().isBlank(), "Missing Russian tip");
		}
	}

	private static void verifyFilming() {
		for (int[] viewport : new int[][] {{640,360,346}, {427,240,346}, {320,180,308}, {1920,1080,346}}) {
			int left = (viewport[0] - viewport[2]) / 2;
			for (float progress : new float[] {0, 0.25f, 0.5f, 0.75f, 1}) {
				var tab = FilmingTabLayout.at(viewport[0], left, 6, viewport[2], progress);
				var settings = FilmingTabLayout.at(viewport[0], left, 6, viewport[2], progress, 1);
				check(settings.x() >= 0 && settings.x() + settings.width() <= viewport[0], "Settings tab leaves viewport");
				check(settings.inHeader() ? tab.x() + tab.width() < settings.x() : tab.y() + tab.height() < settings.y(), "Side tabs overlap");
				check(tab.x() >= 0 && tab.x() + tab.width() <= viewport[0], "Filming tab leaves the viewport");
				check(tab.inHeader() || tab.x() + tab.width() <= left || tab.x() >= left + viewport[2], "Side tab overlaps builder controls");
				check(!tab.inHeader() || tab.y()+tab.height() < 6+18, "Compact filming tab overlaps the ETA header");
			}
			check(FilmingTabLayout.at(viewport[0],left,6,viewport[2],1).width()
				== FilmingTabLayout.at(viewport[0],left,6,viewport[2],0).width()*3, "Tab must expand to exactly three times its width");
		}
		for (var action : dev.grandbuilder.network.FilmingAction.values()) {
			check(dev.grandbuilder.network.FilmingAction.byId(action.ordinal()) == action, "Filming action ID changed");
			var request = new dev.grandbuilder.network.FilmingToolsPayload(action.ordinal());
			var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			try {
				dev.grandbuilder.network.FilmingToolsPayload.CODEC.encode(buffer, request);
				check(dev.grandbuilder.network.FilmingToolsPayload.CODEC.decode(buffer).equals(request), "Filming request did not roundtrip");
			} finally { buffer.release(); }
			if (action.changesTime()) check(action.dayTime() >= 0 && action.dayTime() < 24000, "Invalid filming time");
		}
		check(dev.grandbuilder.network.FilmingAction.byId(-1) == null && dev.grandbuilder.network.FilmingAction.byId(999) == null,
			"Invalid filming action must never run a command");
		var state = new dev.grandbuilder.network.FilmingStatePayload(true, true, 12000, 2, 1);
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			dev.grandbuilder.network.FilmingStatePayload.CODEC.encode(buffer, state);
			check(dev.grandbuilder.network.FilmingStatePayload.CODEC.decode(buffer).equals(state), "Filming feedback lost server state");
		} finally { buffer.release(); }
		System.out.println("Filming tab bounds, exact 3x expansion, bounded actions and bidirectional codecs verified.");
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
		check(preferences.theme() == BuilderTheme.CURRENT && !preferences.structureList() && !preferences.effectList() && !preferences.defaultTerrain()
			&& preferences.defaultSpeed() == dev.grandbuilder.build.BuildSpeed.NORMAL, "Migration did not supply safe defaults");
		preferences.setTheme(BuilderTheme.LIGHT); preferences.setStructureList(true); preferences.setDefaultTerrain(true);
		preferences.setEffectList(true);
		check(preferences.cinematicCamera(), "Existing preferences did not enable the optional new mode camera");
		preferences.setCinematicCamera(false);
		check(!BuilderTheme.LIGHT.textShadow() && BuilderTheme.CURRENT.textShadow() && BuilderTheme.DARK.textShadow(), "Light text keeps its dark shadow");
		preferences.setDefaultSpeed(dev.grandbuilder.build.BuildSpeed.OVERDRIVE);
		preferences.setDefaultEffect(dev.grandbuilder.build.BuildEffectMode.HEROBRINE);
		preferences.setDefaultPlacement(dev.grandbuilder.build.PlacementPolicy.REPLACE);
		preferences = new BuilderTipPreferences(file);
		check(preferences.theme() == BuilderTheme.LIGHT && !preferences.cinematicCamera() && preferences.structureList() && preferences.effectList() && preferences.defaultTerrain()
			&& preferences.defaultSpeed() == dev.grandbuilder.build.BuildSpeed.OVERDRIVE
			&& preferences.defaultEffect() == dev.grandbuilder.build.BuildEffectMode.HEROBRINE
			&& preferences.defaultPlacement() == dev.grandbuilder.build.PlacementPolicy.REPLACE, "Settings did not survive restart");
		check(preferences.takeAutomaticTip(now + 2 * cooldown + 1) == -1, "Saving defaults erased tip cooldown");
		preferences.resetDefaults(); preferences = new BuilderTipPreferences(file);
		check(preferences.theme() == BuilderTheme.CURRENT && !preferences.defaultTerrain() && !preferences.effectList() && preferences.enabled()
			&& preferences.takeAutomaticTip(now + 2 * cooldown + 1) == -1, "Reset lost cooldown or safe defaults");
		try (var files = Files.list(file.getParent())) { check(files.count() == 1, "Atomic save leaked temporary files"); }
		Files.writeString(file, "{\"nextTip\":-1,\"lastShownAtMillis\":-5}");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == BuilderTipPreferences.TIP_COUNT - 1, "Missing fields or invalid indices not normalized");
		Files.writeString(file, "{\"theme\":\"unknown\",\"defaultSpeed\":null,\"defaultEffect\":null,\"defaultPlacement\":null}");
		preferences = new BuilderTipPreferences(file);
		check(preferences.theme() == BuilderTheme.CURRENT && preferences.defaultSpeed() == dev.grandbuilder.build.BuildSpeed.NORMAL
			&& preferences.defaultEffect() == dev.grandbuilder.build.BuildEffectMode.STANDARD
			&& preferences.defaultPlacement() == dev.grandbuilder.build.PlacementPolicy.PRESERVE, "Unknown settings were not normalized");
		Files.writeString(file, "null");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == 0, "Null preferences did not recover");
		Files.writeString(file, "broken json");
		preferences = new BuilderTipPreferences(file);
		check(preferences.enabled() && preferences.takeAutomaticTip(now) == 0, "Malformed preferences did not recover");
	}

	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
