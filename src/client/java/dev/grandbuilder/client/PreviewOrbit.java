package dev.grandbuilder.client;

import dev.grandbuilder.network.StructurePreviewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class PreviewOrbit {
	public interface View {
		PreviewOrbit orbit();
		int previewKind();
		int viewportLeft();
	}
	public record Pose(Vec3 position, float yaw, float pitch) { }
	private float yaw=-135, pitch=25;
	private double zoom=1;
	public void drag(double dx, double dy) { yaw+=(float)dx*0.6f; pitch=Math.clamp(pitch+(float)dy*0.5f,-75,75); }
	public void zoom(double delta) { zoom=Math.clamp(zoom*Math.exp(-delta*0.12),0.25,3); }
	public void reset() { yaw=-135; pitch=25; zoom=1; }
	public Pose pose(View view) {
		StructurePreviewPayload model=StructurePreviewClientState.get(view.previewKind());
		Minecraft client=Minecraft.getInstance();
		if (model==null || client.screen==null) return null;
		double w=model.max().getX()-model.min().getX()+1, h=model.max().getY()-model.min().getY()+1;
		double d=model.max().getZ()-model.min().getZ()+1;
		Vec3 center=new Vec3(model.min().getX()+w/2,model.min().getY()+h/2,model.min().getZ()+d/2);
		return pose(center,w,h,d,client.screen.width,client.screen.height,view.viewportLeft(),client.options.fov().get());
	}
	Pose pose(Vec3 center,double w,double h,double d,int screenWidth,int screenHeight,int viewportLeft,double fov) {
		double aspect=(double)screenWidth/screenHeight;
		double visibleAspect=(double)Math.max(1,screenWidth-viewportLeft)/screenHeight;
		double tangent=Math.tan(Math.toRadians(fov)/2);
		double verticalFraction=(double)Math.max(1,screenHeight-(viewportLeft==0?60:16))/screenHeight;
		double radius=Math.sqrt(w*w+h*h+d*d)/2;
		double offsetRatio=(double)viewportLeft/screenWidth*tangent*aspect;
		double distance=Math.max(6,radius/Math.sin(Math.atan(tangent*Math.min(verticalFraction,visibleAspect)))*1.15*zoom);
		// Stay inside the preview's 512-block visibility radius, including the side-panel offset.
		distance=Math.min(480/Math.sqrt(1+offsetRatio*offsetRatio),distance);
		double y=Math.toRadians(yaw), p=Math.toRadians(pitch);
		Vec3 forward=new Vec3(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
		Vec3 right=new Vec3(-Math.cos(y),0,-Math.sin(y));
		double offset=offsetRatio*distance;
		return new Pose(center.subtract(forward.scale(distance)).subtract(right.scale(offset)),yaw,pitch);
	}
}
