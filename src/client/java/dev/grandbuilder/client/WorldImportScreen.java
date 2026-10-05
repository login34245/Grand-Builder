package dev.grandbuilder.client;

import dev.grandbuilder.build.WorldMapImporter;
import dev.grandbuilder.network.WorldImportRequestPayload;
import dev.grandbuilder.network.WorldImportStatePayload;
import dev.grandbuilder.network.StructurePreviewPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class WorldImportScreen extends Screen implements PreviewOrbit.View {
	private final PreviewOrbit orbit=new PreviewOrbit();
	private int knownRevision=-1, sourceIndex, candidateIndex;
	private boolean requestedList;
	private boolean copying, copyFailed;
	private boolean previousHideGui;
	private Button saveButton;
	public WorldImportScreen() { super(Component.translatable("screen.grand_builder.import.title")); }
	private static void send(int action, String source, int candidate, int face, int delta) {
		ClientPlayNetworking.send(new WorldImportRequestPayload(action,source,candidate,face,delta));
	}
	private void send(int action) { send(action,"",0,0,0); }
	private int panelWidth() { return Math.min(width-12,Math.min(302,Math.max(210,(int)(width*0.45)))); }
	private int rowHeight() { return height<300 ? 16 : 20; }
	private boolean compact() { return height<300; }
	private int boundRow(int face, int start, int step) { return start+(compact()?face/2:face)*step; }
	private int boundX(int face, int inner, int content) { return inner+(compact() && face%2==1 ? (content+8)/2:0); }
	private Component fit(Component text, int width) {
		String value=text.getString();
		if (font.width(value)<=width-8) return text;
		return Component.literal(font.plainSubstrByWidth(value,Math.max(12,width-26)).trim()+"...");
	}
	private Button button(Component label,int x,int y,int width,int height,java.util.function.Consumer<Button> action) {
		Component fitted=fit(label,width);
		Button widget=addRenderableWidget(Button.builder(fitted,action::accept).bounds(x,y,Math.max(12,width),height).build());
		if (!fitted.getString().equals(label.getString())) widget.setTooltip(Tooltip.create(label));
		return widget;
	}
	@Override protected void init() {
		if (!requestedList) { send(WorldImportRequestPayload.LIST); requestedList=true; }
		WorldImportStatePayload state=WorldImportClientState.snapshot();
		int panel=panelWidth(), left=6, inner=left+10, content=panel-20, h=rowHeight();
		int sourceY=34, toolsY=sourceY+h+3, candidateY=toolsY+h+7;
		int boundsY=candidateY+h+18, step=h<18?17:22;
		int nav=22;
		button(Component.literal("<"),inner,sourceY,nav,h,b -> { changeSource(-1); });
		button(sourceName(state),inner+nav+3,sourceY,content-2*nav-6,h,b -> changeSource(1));
		button(Component.literal(">"),inner+content-nav,sourceY,nav,h,b -> changeSource(1));
		button(Component.translatable("screen.grand_builder.import.folder"),inner,toolsY,(content-4)/2,h,b -> openFolder());
		Button scan=button(Component.translatable("screen.grand_builder.import.scan"),inner+(content-4)/2+4,toolsY,
			(content-4)/2,h,b -> scanCurrent());
		scan.active=!copying && !state.sources().isEmpty() && state.status()!=WorldImportStatePayload.SCANNING
			&& state.status()!=WorldImportStatePayload.LOADING;
		button(Component.literal("<"),inner,candidateY,nav,h,b -> changeCandidate(-1));
		button(candidateName(state),inner+nav+3,candidateY,content-2*nav-6,h,b -> selectCandidate());
		button(Component.literal(">"),inner+content-nav,candidateY,nav,h,b -> changeCandidate(1));
		for (int face=0;face<6;face++) {
			int row=boundRow(face,boundsY,step), index=face;
			int columnX=boundX(face,inner,content), columnWidth=compact()?(content-8)/2:content;
			button(Component.literal("-"),columnX+columnWidth-42,row,19,h,b -> adjust(index,-1));
			button(Component.literal("+"),columnX+columnWidth-20,row,19,h,b -> adjust(index,1));
		}
		int footerY=Math.max(boundsY+(compact()?3:6)*step+4,height-h-8);
		int footerW=(content-8)/3;
		Button view=button(Component.translatable("screen.grand_builder.import.view"),inner,footerY,footerW,h,b -> minecraft.setScreen(new StructureInspectScreen(StructurePreviewPayload.IMPORT, this)));
		this.saveButton=button(Component.translatable("screen.grand_builder.import.save"),inner+footerW+4,footerY,footerW,h,b -> send(WorldImportRequestPayload.SAVE));
		button(Component.translatable("gui.back"),inner+(footerW+4)*2,footerY,content-(footerW+4)*2,h,b -> onClose());
		boolean ready=state.selectedCandidate()>=0 && (state.status()==WorldImportStatePayload.READY || state.status()==WorldImportStatePayload.SAVED);
		view.active=ready; saveButton.active=ready;
		knownRevision=WorldImportClientState.revision();
	}
	private Component sourceName(WorldImportStatePayload state) {
		if (state.sources().isEmpty()) return Component.translatable("screen.grand_builder.import.no_worlds");
		sourceIndex=Math.floorMod(sourceIndex,state.sources().size());
		return Component.literal(state.sources().get(sourceIndex).name());
	}
	private Component candidateName(WorldImportStatePayload state) {
		if (state.candidates().isEmpty()) return Component.translatable("screen.grand_builder.import.no_candidates");
		candidateIndex=Math.floorMod(candidateIndex,state.candidates().size());
		WorldMapImporter.Bounds b=state.candidates().get(candidateIndex).bounds();
		return Component.translatable(state.candidates().get(candidateIndex).partial()
			? "screen.grand_builder.import.section" : "screen.grand_builder.import.candidate",candidateIndex+1,state.candidates().size(),
			b.width(),b.height(),b.depth());
	}
	private void changeSource(int direction) {
		WorldImportStatePayload state=WorldImportClientState.snapshot();
		if (state.sources().isEmpty()) return;
		sourceIndex=Math.floorMod(sourceIndex+direction,state.sources().size());
		rebuildWidgets();
	}
	private void scanCurrent() {
		WorldImportStatePayload state=WorldImportClientState.snapshot();
		if (state.sources().isEmpty()) return;
		copyFailed=false;
		candidateIndex=0;
		send(WorldImportRequestPayload.SCAN,state.sources().get(sourceIndex).id(),0,0,0);
	}
	private void changeCandidate(int direction) {
		WorldImportStatePayload state=WorldImportClientState.snapshot();
		if (state.candidates().isEmpty()) return;
		candidateIndex=Math.floorMod(candidateIndex+direction,state.candidates().size());
		selectCandidate();
	}
	private void selectCandidate() {
		WorldImportStatePayload state=WorldImportClientState.snapshot();
		if (!state.candidates().isEmpty()) send(WorldImportRequestPayload.SELECT,"",candidateIndex,0,0);
	}
	private void adjust(int face,int delta) {
		if (WorldImportClientState.snapshot().selectedCandidate()<0) return;
		send(WorldImportRequestPayload.RESIZE,"",0,face,delta);
	}
	private void openFolder() {
		try {
			Files.createDirectories(WorldMapImporter.importDirectory());
			Util.getPlatform().openUri(WorldMapImporter.importDirectory().toAbsolutePath().toUri().toString());
		} catch (Exception exception) {
			if (minecraft!=null) minecraft.keyboardHandler.setClipboard(WorldMapImporter.importDirectory().toAbsolutePath().toString());
		}
	}
	@Override public void tick() {
		super.tick();
		if (WorldImportClientState.revision()!=knownRevision) {
			WorldImportStatePayload state=WorldImportClientState.snapshot();
			if (!state.selectedSource().isEmpty()) for (int i=0;i<state.sources().size();i++)
				if (state.sources().get(i).id().equals(state.selectedSource())) { sourceIndex=i; break; }
			if (state.selectedCandidate()>=0) candidateIndex=state.selectedCandidate();
			if (!state.savedKey().isBlank()) BuilderMenuScreen.rememberStructure(state.savedKey());
			rebuildWidgets();
		}
	}
	@Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
		int panel=panelWidth(),right=6+panel,h=rowHeight();
		BuilderTheme theme = BuilderTheme.current();
		graphics.fill(0,0,width,height,0x22060D14);
		graphics.fill(3,3,right+2,height-3,theme.border);
		graphics.fill(6,6,right-1,height-6,theme.panelTop);
		graphics.drawString(font,title,16,11,theme.text);
		WorldImportStatePayload state=WorldImportClientState.snapshot();
		String status=Component.translatable(copying ? "screen.grand_builder.import.copying" : copyFailed
			? "screen.grand_builder.import.copy_failed" : "screen.grand_builder.import.status."+state.status()).getString();
		graphics.drawString(font,font.plainSubstrByWidth(status,panel-24),16,23,theme.muted);
		int candidateY=34+h+3+h+7;
		if (state.selectedCandidate()>=0) {
			WorldMapImporter.Bounds b=state.bounds();
			String size=Component.translatable("screen.grand_builder.import.size",b.width(),b.height(),b.depth()).getString();
			graphics.drawString(font,font.plainSubstrByWidth(size,panel-24),16,candidateY+h+4,theme.accent);
		}
		int boundsY=candidateY+h+18, step=h<18?17:22;
		String[] labels={"X-","X+","Y-","Y+","Z-","Z+"};
		int[] values={state.bounds().minX(),state.bounds().maxX(),state.bounds().minY(),state.bounds().maxY(),
			state.bounds().minZ(),state.bounds().maxZ()};
		for (int face=0;face<6;face++) {
			int y=boundRow(face,boundsY,step)+4;
			int columnX=boundX(face,16,panel-20), columnWidth=compact()?(panel-28)/2:panel-20;
			graphics.drawString(font,labels[face],columnX,y,theme.muted);
			String value=Integer.toString(values[face]);
			graphics.drawString(font,value,columnX+columnWidth-47-font.width(value),y,theme.text);
		}
		graphics.fill(15,boundsY-5,right-11,boundsY-4,0x667DA9C2);
		super.render(graphics,mouseX,mouseY,partialTick);
	}
	@Override public void onClose() {
		send(WorldImportRequestPayload.CLOSE);
		minecraft.setScreen(new BuilderMenuScreen());
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) { }
	@Override public void added() { previousHideGui=minecraft.options.hideGui; minecraft.options.hideGui=true; }
	@Override public void removed() { minecraft.options.hideGui=previousHideGui; }
	@Override public void onFilesDrop(List<Path> paths) {
		if (copying || paths.isEmpty()) return;
		copying=true; copyFailed=false; rebuildWidgets();
		CompletableFuture.runAsync(() -> {
			try { WorldImportFiles.stage(paths); }
			catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
		}).whenComplete((ignored,error) -> minecraft.execute(() -> {
			copying=false; copyFailed=error!=null;
			if (error!=null) dev.grandbuilder.GrandBuilderMod.LOGGER.warn("Could not import dropped world",error);
			if (minecraft.screen==this) { send(WorldImportRequestPayload.LIST); rebuildWidgets(); }
		}));
	}
	@Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
		if (event.button()==0 && event.x()>viewportLeft()) { orbit.drag(dx,dy); return true; }
		return super.mouseDragged(event,dx,dy);
	}
	@Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
		if (x>viewportLeft()) { orbit.zoom(vertical); return true; }
		return super.mouseScrolled(x,y,horizontal,vertical);
	}
	@Override public PreviewOrbit orbit() { return orbit; }
	@Override public int previewKind() { return StructurePreviewPayload.IMPORT; }
	@Override public int viewportLeft() { return panelWidth()+12; }
}
