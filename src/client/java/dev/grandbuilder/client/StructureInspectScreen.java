package dev.grandbuilder.client;

import dev.grandbuilder.network.StructureInspectRequestPayload;
import dev.grandbuilder.network.StructurePreviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class StructureInspectScreen extends Screen implements PreviewOrbit.View {
	private final PreviewOrbit orbit=new PreviewOrbit();
	private final int kind;
	private boolean previousHideGui;
	public StructureInspectScreen(int kind) { super(Component.translatable("screen.grand_builder.inspect_title")); this.kind=kind; }
	@Override protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("gui.back"),b -> onClose()).bounds(8,height-28,90,20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.grand_builder.inspect_reset"),b -> orbit.reset()).bounds(width-118,height-28,110,20).build());
	}
	@Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
		graphics.fill(0,0,width,26,0xB0172738);
		graphics.drawCenteredString(font,title,width/2,9,0xFFE8F4FF);
		super.render(graphics,mouseX,mouseY,partialTick);
	}
	@Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
		if (event.button()==0 && event.y()>26 && event.y()<height-32) { orbit.drag(dx,dy); return true; }
		return super.mouseDragged(event,dx,dy);
	}
	@Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
		orbit.zoom(vertical); return true;
	}
	@Override public void onClose() {
		if (kind==StructurePreviewPayload.IMPORT) minecraft.setScreen(new WorldImportScreen());
		else {
			ClientPlayNetworking.send(new StructureInspectRequestPayload(""));
			minecraft.setScreen(new BuilderMenuScreen());
		}
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) { }
	@Override public void added() { previousHideGui=minecraft.options.hideGui; minecraft.options.hideGui=true; }
	@Override public void removed() { minecraft.options.hideGui=previousHideGui; }
	@Override public PreviewOrbit orbit() { return orbit; }
	@Override public int previewKind() { return kind; }
	@Override public int viewportLeft() { return 0; }
}
