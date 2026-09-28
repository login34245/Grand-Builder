package dev.grandbuilder.client;

import dev.grandbuilder.build.BuildStartSide;
import dev.grandbuilder.network.BuildEffectPayload;
import java.util.ArrayDeque;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class EffectGeometry {
	public enum Material { SOLID, VEIL, GLOW }

	@FunctionalInterface
	public interface Sink {
		void vertex(Material material, float x, float y, float z, int color);
	}

	private static final double TAU = Math.PI * 2.0;
	private static final int ICE = 0x8FFFF2;
	private static final int GOLD = 0xFFD078;
	private static final int VIOLET = 0xB996FF;

	private EffectGeometry() {
	}

	public static void emit(GrandBuilderClientEffects.Frame frame, Sink sink) {
		Mesh mesh = new Mesh(sink, frame.opacity());
		switch (frame.mode()) {
			case UFO_INVASION -> ufo(mesh, frame);
			case RIFT_BLOOM -> rift(mesh, frame);
			case METEOR_FORGE -> meteor(mesh, frame);
			case CLOCKWORK_GRID -> clockwork(mesh, frame);
			case AURORA_WEAVE -> aurora(mesh, frame);
			case BUILDER_CHARGE -> builderCharge(mesh, frame);
			case REVERSE -> directional(mesh, frame);
			case FLYING_BLOCKS, REVERSE_COLLAPSE, ASSEMBLY_WORKSHOP, SCALE_MODEL -> KineticGeometry.emit(frame, mesh);
			default -> { }
		}
	}

	public static void emitHerobrine(GrandBuilderClientEffects.ActorFrame f, Sink sink) {
		double appear = ease(f.age() / 0.8);
		Mesh m = new Mesh(sink, f.opacity() * (float) (0.55 + appear * 0.45));
		Material body = f.ghost() ? Material.VEIL : Material.SOLID;
		double yaw = Math.atan2(f.targetX(), f.targetZ());
		double reach = f.age() < f.contactTick() ? ease(f.age() / f.contactTick())
			: 1 - ease((f.age() - f.contactTick()) / Math.max(1, f.duration() - f.contactTick()));
		m.push(0, 0, 0);
		m.rotate(0, yaw, 0);
		for (int leg = -1; leg <= 1; leg += 2) {
			m.push(leg * 0.125, 0.375, 0);
			m.box(0.125, 0.375, 0.125, 0x4145A0, body, 1);
			m.push(0, -0.32, 0.01);
			m.box(0.126, 0.055, 0.136, 0x44434A, body, 1);
			m.pop();
			m.pop();
		}
		m.push(0, 1.125, 0);
		m.box(0.25, 0.375, 0.125, 0x1B9FAD, body, 1);
		m.push(0, 0.26, 0.127);
		m.box(0.07, 0.10, 0.004, 0xB58262, body, 1);
		m.pop();
		for (int i = -1; i <= 1; i += 2) {
			m.push(i * 0.19, -0.13, 0.128);
			m.box(0.026, 0.16, 0.004, 0x178492, body, 1);
			m.pop();
		}
		m.pop();
		for (int arm = -1; arm <= 1; arm += 2) {
			m.push(arm * 0.375, 1.40, 0);
			m.rotate(arm == -1 ? -1.32 * reach : -0.14, 0, arm * 0.035);
			m.push(0, -0.13, 0);
			m.box(0.125, 0.17, 0.125, 0x1995A4, body, 1);
			m.pop();
			m.push(0, -0.49, 0);
			m.box(0.125, 0.20, 0.125, 0xB88769, body, 1);
			m.pop();
			if (arm == -1 && !f.ghost() && (f.dismantling() ? f.age() >= f.contactTick() : f.age() < f.contactTick())) {
				m.push(0, -0.74, 0.06);
				m.rotate(0.12, 0.25, 0);
				m.box(0.21, 0.21, 0.21, f.blockColor(), Material.SOLID, 1);
				m.pop();
			}
			m.pop();
		}
		m.push(0, 1.75, 0);
		m.rotate(0.12 + reach * 0.10, 0, 0);
		m.box(0.25, 0.25, 0.25, 0x473329, body, 1);
		int[] skin = {0xB78564, 0xBD8B6B, 0xC49473, 0xAA7758};
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				int color = skin[(row + col * 3) % skin.length];
				if (row < 2 || (row == 2 && (col == 0 || col == 7))) color = (col % 3 == 0) ? 0x39281F : 0x4B3428;
				if (row == 4 && (col == 3 || col == 4)) color = 0x956048;
				if (row >= 6 && col >= 2 && col <= 5) color = row == 6 && (col == 3 || col == 4) ? 0x6A4334 : 0x50362A;
				boolean eye = row == 3 && (col == 1 || col == 2 || col == 5 || col == 6);
				m.quad(eye ? Material.GLOW : body,
					new double[] {-0.25 + col * 0.0625, 0.25 - row * 0.0625, 0.252},
					new double[] {-0.25 + (col+1) * 0.0625, 0.25 - row * 0.0625, 0.252},
					new double[] {-0.25 + (col+1) * 0.0625, 0.25 - (row+1) * 0.0625, 0.252},
					new double[] {-0.25 + col * 0.0625, 0.25 - (row+1) * 0.0625, 0.252},
					eye ? 0xFFFFFF : color, 1);
			}
		}
		for (int eye = -1; eye <= 1; eye += 2) {
			m.push(eye * 0.125, 0.03125, 0.26);
			m.box(0.080, 0.045, 0.014, 0xBBD8FF, Material.GLOW, 0.16);
			m.pop();
		}
		m.pop();
		m.pop();
		if (!f.ghost()) {
			double disruption = 1 - ease(f.age() / 1.5);
			for (int i = 0; i < 10; i++) {
				double angle = i * 2.399;
				m.push(Math.cos(angle) * (0.35 + disruption * 0.4), 0.15 + i * 0.18, Math.sin(angle) * (0.35 + disruption * 0.4));
				m.rotate(disruption * 1.2, angle, disruption * -0.8);
				m.box(0.015, 0.16, 0.04, 0x182330, Material.VEIL, disruption * 0.6);
				m.box(0.007, 0.07, 0.025, 0xDCEAFF, Material.GLOW, disruption * 0.4);
				m.pop();
			}
			double seal = Math.max(0, 1 - Math.abs(f.age() - f.contactTick()) / 1.6);
			m.push(f.targetX(), f.targetY() - 0.51, f.targetZ());
			m.rectangle(0.515, 0.515, 0.016, 0xE9F4FF, seal * 0.50);
			m.push(0, 1.02, 0);
			m.rectangle(0.515, 0.515, 0.016, 0xE9F4FF, seal * 0.50);
			m.pop();
			for (int x = -1; x <= 1; x += 2) {
				for (int z = -1; z <= 1; z += 2) m.tube(x * 0.515, 0, z * 0.515, x * 0.515, 1.02, z * 0.515,
					0.016, 0xE9F4FF, Material.GLOW, seal * 0.50);
			}
			m.pop();
		}
	}

	private static void builderCharge(Mesh m, GrandBuilderClientEffects.Frame f) {
		if (f.dismantling() && f.phase() == BuildEffectPayload.PHASE_STOP) { dismantleWave(m,f); return; }
		boolean dismantleBurst = f.dismantling() && f.phase() != BuildEffectPayload.PHASE_ARRIVAL;
		double t = dismantleBurst ? clamp(f.age()/44) : f.phaseProgress();
		double roof = f.destructive() ? 1.5 : f.height() + 3;
		if (!f.revealing() && !dismantleBurst) {
			double flight = ease(t / 0.78);
			double x = -42 * (1 - flight), z = 26 * (1 - flight);
			double y = roof + 32 * (1 - flight) + Math.sin(flight * Math.PI) * 14;
			for (int i = 0; i < 12; i++) {
				double a = ease((t - i * 0.013) / 0.78), b = ease((t - (i+1) * 0.013) / 0.78);
				m.tube(-42*(1-a), roof+32*(1-a)+Math.sin(a*Math.PI)*14, 26*(1-a),
					-42*(1-b), roof+32*(1-b)+Math.sin(b*Math.PI)*14, 26*(1-b),
					0.13 - i*0.008, i < 3 ? 0xBDF5FF : 0x6C8A9C, Material.GLOW, (1-i/12.0)*0.4);
			}
			m.push(x, y + Math.sin(f.motionAge()*0.22)*flight*0.08, z);
			m.rotate((1-flight)*0.6, f.motionAge()*0.07, (1-flight)*0.7);
			m.scale(Math.max(1, Math.min(2.2, Math.sqrt(f.radius()/4))));
			m.lathe(new double[] {0, 0.38, 0.85, 0.95, 0.95, 0.72, 0},
				new double[] {-1.45, -1.32, -0.85, -0.6, 0.65, 0.95, 1.02}, 32, 0x687360);
			for (int band = -1; band <= 1; band += 2) {
				m.push(0, band*0.55, 0);
				m.lathe(new double[] {0.95, 0.985, 0.985, 0.95}, new double[] {-0.10, -0.08, 0.08, 0.10}, 32, 0xD9BF52);
				m.pop();
			}
			for (int fin = 0; fin < 4; fin++) {
				m.push(0, 0, 0);
				m.rotate(0, fin*Math.PI/2, 0);
				m.push(0, 0.88, 1.04);
				m.box(0.055, 0.46, 0.42, 0x38464B, Material.SOLID, 1);
				m.push(0, 0.35, 0.24);
				m.box(0.06, 0.045, 0.15, 0xD9BF52, Material.SOLID, 1);
				m.pop();
				m.pop();
				m.push(0, 0.15, 0.958);
				m.box(0.32, 0.25, 0.05, 0x263137, Material.SOLID, 1);
				m.push(0, 0, 0.055);
				m.box(0.20, 0.04, 0.006, 0xFF4940, Material.GLOW, 0.35 + Math.pow(Math.sin(f.motionAge()*0.6), 8)*0.60);
				m.pop();
				m.pop();
				m.pop();
			}
			m.pop();
			return;
		}
		if (t >= 1) {
			if (f.dismantling()) dismantleWave(m, f);
			return;
		}
		double expansion = 1 - Math.pow(1-t, 3);
		double radius = (f.radius()+6)*expansion+0.4;
		m.push(0, roof, 0);
		m.sphere(radius, 0xC9F4FF, (1-ease(t/0.42))*0.085);
		m.torus(radius, 0.08*(1-t)+0.01, 0, 0xFFE7A5, (1-t)*0.65);
		m.rotate(0, 0, Math.PI/2);
		m.torus(radius*0.87, 0.035, 0, 0xC3F6FF, (1-t)*0.30);
		m.pop();
		double ignition = 1 - ease(t/0.34);
		m.push(0, roof, 0);
		m.sphere(0.8 + 4.5*ease(t/0.18), 0xFFE4B0, ignition*0.14);
		m.sphere(0.5 + 2.8*ease(t/0.14), 0xEDFAFF, ignition*0.24);
		m.pop();
		for (int ray = 0; ray < 16; ray++) {
			double a = ray*2.399;
			double elevation = Math.sin(ray*1.7)*0.62;
			double length = (f.radius()+3)*ease(t/0.22);
			for (int segment = 0; segment < 4; segment++) {
				double r0 = length*(0.45+segment*0.13), r1 = length*(0.58+segment*0.13);
				m.tube(Math.cos(a)*r0, roof+elevation*r0, Math.sin(a)*r0,
					Math.cos(a)*r1, roof+elevation*r1, Math.sin(a)*r1, 0.065*(1-segment*0.17),
					segment < 2 ? 0xFFF2D5 : 0x7CEBFF, Material.GLOW, ignition*(0.65-segment*0.12));
			}
		}
		for (int i = 0; i < 24; i++) {
			double a = i*2.399, distance = (f.radius()+4)*ease(t);
			m.push(Math.cos(a)*distance, roof + Math.sin(t*Math.PI)*(4+i%4) - t*t*4, Math.sin(a)*distance);
			m.rotate(t*(4+i%3)+i, t*5, a+t*3);
			m.box(0.06, 0.22+i%3*0.07, 0.30, i%3==0 ? 0xC3AD54 : 0x56655D, Material.SOLID, 1-ease((t-0.45)/0.55));
			m.push(0, 0, 0.305);
			m.box(0.03, 0.12, 0.012, 0xBAEBFF, Material.GLOW, (1-t)*0.3);
			m.pop();
			m.pop();
		}
		// The blast leaves a fitted assembly cage, then a single scan resolves the house.
		double cage = ease(t/0.12)*(1-ease((t-0.45)/0.55));
		double halfX = f.width()*0.5+0.18, halfZ = f.depth()*0.5+0.18;
		for (int x = -1; x <= 1; x += 2) {
			for (int z = -1; z <= 1; z += 2) {
				m.tube(x*halfX, 0, z*halfZ, x*halfX, f.height()+0.18, z*halfZ, 0.035, ICE, Material.GLOW, cage*0.55);
				for (double y : new double[] {0.15, f.height()+0.18}) {
					m.tube(x*halfX, y, z*halfZ, x*(halfX-Math.min(2,halfX)), y, z*halfZ, 0.065, 0xFFE7A5, Material.GLOW, cage*0.75);
					m.tube(x*halfX, y, z*halfZ, x*halfX, y, z*(halfZ-Math.min(2,halfZ)), 0.065, 0xFFE7A5, Material.GLOW, cage*0.75);
				}
			}
		}
		m.push(0, f.height()*ease((t-0.06)/0.75)+0.1, 0);
		m.rectangle(halfX, halfZ, 0.04, ICE, cage*0.55);
		m.pop();
		blastWave(m, f.radius()+7, t);
		if (f.dismantling()) dismantleWave(m, f);
	}

	private static void blastWave(Mesh m, double size, double t) {
		double radius = 0.4 + size*(1-Math.pow(1-t,3));
		double fade = 1-ease((t-0.45)/0.55);
		m.push(0, 0.25+Math.sin(t*Math.PI)*0.6, 0);
		m.torus(radius, 0.11*(1-t)+0.025, 0, 0xB7F3FF, fade*0.65);
		for (int i = 0; i < 48; i++) {
			double a=i*TAU/48,b=(i+1)*TAU/48,inner=Math.max(0.2,radius-1.8*(1-t));
			m.gradientQuad(Math.cos(a)*radius,0,Math.sin(a)*radius,Math.cos(b)*radius,0,Math.sin(b)*radius,
				Math.cos(b)*inner,0.12,Math.sin(b)*inner,Math.cos(a)*inner,0.12,Math.sin(a)*inner,
				0xC8E6EE,fade*0.17,0);
		}
		m.pop();
	}

	private static void dismantleWave(Mesh m, GrandBuilderClientEffects.Frame f) {
		double radius = 0.3 + Math.hypot(f.width(),f.depth())*0.5*Math.sqrt(f.progress());
		double pulse = 0.45 + Math.sin(f.motionAge()*0.4)*0.15;
		m.push(0,0.18,0);
		m.torus(radius,0.09,0,0xFFCC82,pulse);
		m.cone(radius,radius+0.12,Math.min(f.height(),1.2),0x91E5FF,pulse*0.08);
		m.pop();
	}

	private static void directional(Mesh m, GrandBuilderClientEffects.Frame f) {
		int facing = f.orderId()/8;
		double rotation = facing == 5 ? -Math.PI/2 : facing == 3 ? Math.PI : facing == 4 ? Math.PI/2 : 0;
		double width = facing == 5 || facing == 4 ? f.depth() : f.width();
		double depth = facing == 5 || facing == 4 ? f.width() : f.depth();
		double x=width*0.5+0.12,z=depth*0.5+0.12,h=f.height();
		double progress=f.progress();
		double alpha=0.40+Math.sin(f.motionAge()*0.18)*0.08;
		m.push(0,0,0);
		m.rotate(0,rotation,0);
		for(int sx=-1;sx<=1;sx+=2) for(int sz=-1;sz<=1;sz+=2)
			m.tube(sx*x,0,sz*z,sx*x,h,sz*z,0.023,0xA5C8DE,Material.GLOW,0.13);
		BuildStartSide side=BuildStartSide.byId(f.orderId()&7);
		if(side==BuildStartSide.TOP) {
			m.push(0,h*(1-progress)+0.08,0);
			m.rectangle(x,z,0.04,ICE,alpha);
			m.pop();
		} else if(side==BuildStartSide.FRONT || side==BuildStartSide.BACK) {
			double p=(side==BuildStartSide.FRONT?-1:1)*z*(1-2*progress);
			m.tube(-x,0,p,x,0,p,0.045,ICE,Material.GLOW,alpha);
			m.tube(-x,h,p,x,h,p,0.045,ICE,Material.GLOW,alpha);
			m.tube(-x,0,p,-x,h,p,0.045,ICE,Material.GLOW,alpha);
			m.tube(x,0,p,x,h,p,0.045,ICE,Material.GLOW,alpha);
		} else {
			double p=(side==BuildStartSide.LEFT?-1:1)*x*(1-2*progress);
			m.tube(p,0,-z,p,0,z,0.045,ICE,Material.GLOW,alpha);
			m.tube(p,h,-z,p,h,z,0.045,ICE,Material.GLOW,alpha);
			m.tube(p,0,-z,p,h,-z,0.045,ICE,Material.GLOW,alpha);
			m.tube(p,0,z,p,h,z,0.045,ICE,Material.GLOW,alpha);
		}
		m.pop();
	}

	public static void emitLightning(GrandBuilderClientEffects.BoltFrame f, Sink sink) {
		double after=Math.max(0,f.age()-f.contactTick());
		double fade=1-ease(after/4.5);
		Mesh m=new Mesh(sink,f.opacity()*(float)fade);
		double height=15+Math.floorMod(f.sequence(),5);
		double growth=clamp(f.age()/Math.max(1,f.contactTick()));
		double strength=f.age()<f.contactTick()?0.18:0.82*Math.exp(-after*0.28);
		double[][] points=new double[13][3];
		for(int i=0;i<=12;i++) {
			double fraction=i/12.0;
			double envelope=Math.sin(fraction*Math.PI);
			points[i]=new double[]{Math.sin(f.sequence()*1.31+i*2.71)*envelope*1.4,height*(1-fraction),
				Math.cos(f.sequence()*0.91+i*3.93)*envelope*1.2};
		}
		for(int i=0;i<12;i++) {
			double local=clamp(growth*12-i);
			if(local<=0) break;
			double[] a=points[i], b=points[i+1];
			double bx=mix(a[0],b[0],local),by=mix(a[1],b[1],local),bz=mix(a[2],b[2],local);
			m.tube(a[0],a[1],a[2],bx,by,bz,0.085,0x75B9FF,Material.GLOW,strength*0.35);
			m.tube(a[0],a[1],a[2],bx,by,bz,0.027,0xF4FBFF,Material.GLOW,strength);
			if((i==3 || i==6 || i==8) && local==1) {
				double angle=f.sequence()*0.71+i;
				for(int j=0;j<3;j++) {
					double d0=j*1.0,d1=(j+1)*1.0;
					m.tube(b[0]+Math.cos(angle)*d0,b[1]-d0*0.6,b[2]+Math.sin(angle)*d0,
						b[0]+Math.cos(angle+0.16)*d1,b[1]-d1*0.6,b[2]+Math.sin(angle+0.16)*d1,
						0.015,0xBFDFFF,Material.GLOW,strength*(0.40-j*0.10));
				}
			}
		}
		if(f.age()>=f.contactTick()) {
			m.push(0,-0.48,0);
			m.torus(0.25+after*0.20,0.025,0,0xA4DFFF,strength*0.42);
			m.pop();
			for(int i=0;i<6;i++) {
				double a=i*TAU/6+f.sequence(),r=0.22+after*0.12;
				m.push(Math.cos(a)*r,0.08+after*0.13,Math.sin(a)*r);
				m.rotate(a,after*0.35,a*0.4);
				m.box(0.015,0.09,0.015,f.dismantling()?0xFFD2A2:0xBBE9FF,Material.GLOW,strength*0.65);
				m.pop();
			}
		}
	}

	private static void ufo(Mesh m, GrandBuilderClientEffects.Frame f) {
		double t = f.phaseProgress();
		double arrival = f.revealing() ? 1.0 : ease(t / 0.62);
		double departure = f.revealing() ? t * t : 0.0;
		double r = Math.min(12.0, f.radius() * 0.62);
		double roof = f.height() + 9.0;
		double y = roof + (1.0 - arrival) * 24.0 + departure * 35.0;
		m.push(48.0 * (1.0 - arrival) + departure * 18.0, y, -32.0 * (1.0 - arrival));
		m.rotate(0.0, f.motionAge() * 0.018, (1.0 - arrival) * -0.42);
		m.lathe(new double[] {0.0, r * 0.35, r * 0.78, r, r * 0.93, r * 0.50, 0.0},
			new double[] {2.4, 2.2, 0.6, 0.0, -0.65, -1.1, -1.2}, 48, 0x526779);
		m.torus(r * 0.94, 0.10, 0.0, ICE, 0.88);
		m.push(0.0, -0.68, 0.0);
		m.torus(r * 0.74, 0.08, 0.0, ICE, 0.74);
		m.pop();
		m.push(0.0, 1.7, 0.0);
		m.lathe(new double[] {0.0, r * 0.2, r * 0.35, r * 0.36},
			new double[] {1.2, 1.0, 0.4, 0.0}, 32, 0x20394B);
		m.torus(r * 0.36, 0.05, 0.0, 0xD8FFFF, 0.8);
		m.pop();
		for (int i = 0; i < 12; i++) {
			double a = i * TAU / 12.0;
			m.push(Math.cos(a) * r * 0.81, 0.4, Math.sin(a) * r * 0.81);
			m.octahedron(0.18, 0.18, 0.35, ICE, Material.GLOW, 0.9);
			m.pop();
		}
		m.pop();
		double beam = ease((t - 0.47) / 0.28) * (1.0 - ease(departure * 4.0));
		if (f.revealing()) beam = Math.max(0.0, 1.0 - t * 3.0);
		if (beam > 0.01) {
			m.push(0.0, y - 1.2, 0.0);
			m.cone(r * 0.35, f.radius() * 0.86, -(y - 0.1), ICE, 0.12 * beam);
			for (int i = 0; i < 5; i++) {
				double fraction = fract(f.motionAge() * 0.024 + i / 5.0);
				m.push(0.0, -(y - 0.1) * fraction, 0.0);
				m.torus(mix(r * 0.35, f.radius() * 0.86, fraction), 0.04, 0.0, ICE, 0.36 * beam);
				m.pop();
			}
			m.pop();
		}
		if (f.revealing()) shockwave(m, f.radius(), t, ICE);
	}

	private static void rift(Mesh m, GrandBuilderClientEffects.Frame f) {
		double t = f.phaseProgress();
		double open = f.revealing() ? Math.pow(1.0 - t, 0.6) : ease(t / 0.60);
		double r = Math.min(13.0, f.radius() * 0.8) * Math.max(0.03, open);
		m.push(0.0, f.height() * 0.55 + 3.0, 0.0);
		m.rotate(Math.PI / 2.0, 0.0, 0.35);
		m.cone(r * 0.94, 0.0, -1.2, 0x151021, 0.82 * open, Material.VEIL);
		for (int ring = 0; ring < 3; ring++) {
			m.push(0.0, ring * 0.25, 0.0);
			m.torus(r * (1.0 + ring * 0.06), ring == 0 ? 0.13 : 0.04,
				f.motionAge() * 0.018 * (ring % 2 == 0 ? 1 : -1), ring == 0 ? 0xEBDDFF : VIOLET, 0.9 - ring * 0.25);
			m.pop();
		}
		for (int i = 0; i < 14; i++) {
			double a = TAU * i / 14.0 + f.motionAge() * 0.028;
			m.push(Math.cos(a) * r * 1.26, Math.sin(f.motionAge() * 0.08 + i) * 0.45, Math.sin(a) * r * 1.26);
			m.rotate(f.motionAge() * 0.023 + i, a, i * 0.4);
			m.octahedron(0.20, 0.65 + (i % 3) * 0.15, 0.25, 0x423754, Material.SOLID, open);
			m.octahedron(0.07, 0.74 + (i % 3) * 0.15, 0.08, VIOLET, Material.GLOW, open * 0.7);
			m.pop();
		}
		for (int ray = 0; ray < 7; ray++) {
			double a = TAU * ray / 7.0 + f.motionAge() * -0.019;
			for (int j = 0; j < 8; j++) {
				double a0 = a + Math.sin(j * 1.8 + ray) * 0.15;
				double a1 = a + Math.sin((j + 1) * 1.8 + ray) * 0.15;
				double r0 = r * (0.15 + j * 0.10);
				double r1 = r * (0.15 + (j + 1) * 0.10);
				m.tube(Math.cos(a0) * r0, -0.15, Math.sin(a0) * r0,
					Math.cos(a1) * r1, -0.15, Math.sin(a1) * r1, 0.025, VIOLET, Material.GLOW, 0.56 * open);
			}
		}
		m.pop();
		if (f.revealing()) shockwave(m, f.radius(), t, VIOLET);
	}

	private static void meteor(Mesh m, GrandBuilderClientEffects.Frame f) {
		double t = f.phaseProgress();
		double r = Math.min(4.5, f.radius() * 0.35);
		if (!f.revealing()) {
			double flight = Math.pow(t, 1.55);
			double headX = 24.0 * (1.0 - flight);
			double headY = (f.height() + 36.0) * (1.0 - flight) + 0.7;
			double headZ = -14.0 * (1.0 - flight);
			for (int k = 0; k < 16; k++) {
				double oldT = Math.max(0.0, t - k * 0.014);
				double nextT = Math.max(0.0, t - (k + 1) * 0.014);
				double a = Math.pow(oldT, 1.55);
				double b = Math.pow(nextT, 1.55);
				m.tube(24 * (1-a), (f.height()+36)*(1-a)+0.7, -14*(1-a),
					24*(1-b), (f.height()+36)*(1-b)+0.7, -14*(1-b),
					r * (0.70 - k * 0.036), k < 4 ? 0xFFD99A : 0xFF5728, Material.GLOW, (1.0-k/16.0)*0.35);
			}
			m.push(headX, headY, headZ);
			m.rotate(t * 4.0, t * 3.0, t);
			m.lathe(new double[] {0.0, r*0.65, r, r*0.85, r*0.55, 0.0},
				new double[] {r, r*0.65, 0.1, -r*0.50, -r*0.86, -r}, 12, 0x4D3E39);
			for (int i = 0; i < 9; i++) {
				double a = i*TAU/9.0;
				m.tube(0, -r*0.8, 0, Math.cos(a)*r*0.9, r*0.7, Math.sin(a)*r*0.9,
					0.055, 0xFF9D49, Material.GLOW, 0.8);
			}
			m.pop();
		} else {
			shockwave(m, f.radius()*1.25, t, 0xFFAE61);
			for (int i = 0; i < 18; i++) {
				double a = i*TAU/18.0;
				double travel = (f.radius()+5) * ease(t);
				m.push(Math.cos(a)*travel, 0.6 + Math.sin(t*Math.PI)*4.0, Math.sin(a)*travel);
				m.rotate(t*3+i, t*4, t*2);
				m.octahedron(0.25, 0.45, 0.35, 0x5B4B43, Material.SOLID, 1.0-t);
				m.octahedron(0.27, 0.15, 0.37, 0xFF9C48, Material.GLOW, (1.0-t)*0.4);
				m.pop();
			}
		}
	}

	private static void clockwork(Mesh m, GrandBuilderClientEffects.Frame f) {
		double r = Math.min(16.0, f.radius()*0.8);
		double t = f.phaseProgress();
		double exit = f.revealing() ? 1.0 - ease(t) : 1.0;
		m.push(0.0, f.height()+3.5, 0.0);
		m.rotate(0, f.motionAge()*0.007, 0);
		m.torus(r, 0.15, 0, GOLD, 0.7);
		m.torus(r*0.84, 0.045, 0, ICE, 0.65);
		for (int i = 0; i < 36; i++) {
			double a = i*TAU/36;
			m.push(Math.cos(a)*r, 0, Math.sin(a)*r);
			m.rotate(0, -a, 0);
			m.box(0.5, 0.32, 0.20, 0x85765A, Material.SOLID, exit);
			m.pop();
		}
		for (int i = 0; i < 12; i++) {
			double a = i*TAU/12;
			m.tube(Math.cos(a)*r*0.72, 0.12, Math.sin(a)*r*0.72,
				Math.cos(a)*r*0.82, 0.12, Math.sin(a)*r*0.82, 0.05, GOLD, Material.GLOW, exit);
		}
		for (int hand = 0; hand < 2; hand++) {
			double a = f.motionAge() * (hand == 0 ? 0.21 : -0.055);
			m.tube(0, 0.22, 0, Math.cos(a)*r*(hand==0?0.7:0.5), 0.22, Math.sin(a)*r*(hand==0?0.7:0.5),
				hand==0?0.07:0.12, hand==0?ICE:GOLD, Material.GLOW, exit*0.9);
		}
		for (int g = 0; g < 2; g++) {
			m.push(0, -r*0.23, 0);
			m.rotate(Math.PI/2 + Math.sin(f.motionAge()*0.015)*0.25, g*Math.PI/2, f.motionAge()*0.014);
			m.torus(r*(g==0?0.88:0.66), 0.055, 0, g==0?ICE:GOLD, 0.42*exit);
			m.pop();
		}
		m.pop();
		if (!f.revealing()) {
			double scan = fract(f.motionAge()/38.0);
			m.push(0, f.height()*scan+0.2, 0);
			m.rectangle(f.width()*0.52, f.depth()*0.52, 0.055, ICE, 0.56);
			m.pop();
		} else {
			shockwave(m, f.radius(), t, GOLD);
		}
	}

	private static void aurora(Mesh m, GrandBuilderClientEffects.Frame f) {
		double r = f.radius();
		double release = f.revealing() ? f.phaseProgress() : 0;
		for (int band = 0; band < 4; band++) {
			double offset = band * 1.35;
			int color = band % 2 == 0 ? 0x70FFD2 : 0xC99DFF;
			for (int i = 0; i < 64; i++) {
				double u0 = i / 64.0;
				double u1 = (i+1) / 64.0;
				double a0 = -Math.PI*0.85 + u0*Math.PI*1.7 + band*0.58;
				double a1 = -Math.PI*0.85 + u1*Math.PI*1.7 + band*0.58;
				double radius0 = r*(0.9+band*0.08) + Math.sin(u0*14+f.motionAge()*0.024+band)*1.8;
				double radius1 = r*(0.9+band*0.08) + Math.sin(u1*14+f.motionAge()*0.024+band)*1.8;
				double base0 = f.height()+3.0+offset + Math.sin(a0*3+f.motionAge()*0.028)*2.0 + release*9;
				double base1 = f.height()+3.0+offset + Math.sin(a1*3+f.motionAge()*0.028)*2.0 + release*9;
				double x0 = Math.cos(a0)*radius0, z0 = Math.sin(a0)*radius0;
				double x1 = Math.cos(a1)*radius1, z1 = Math.sin(a1)*radius1;
				double alpha = Math.sin(u0*Math.PI)*0.25*(1.0-release);
				m.gradientQuad(x0, base0, z0, x1, base1, z1,
					x1, base1+5.5+Math.sin(a1*5)*1.2, z1, x0, base0+5.5+Math.sin(a0*5)*1.2, z0,
					color, alpha, 0.0);
				m.tube(x0, base0, z0, x1, base1, z1, 0.025, color, Material.GLOW, alpha*2);
			}
		}
		if (f.revealing()) shockwave(m, r, release, ICE);
	}

	private static void shockwave(Mesh m, double radius, double t, int color) {
		double expansion = 1.0-Math.pow(1.0-clamp(t), 3.0);
		m.push(0, 0.12 + t*0.28, 0);
		m.torus((radius+8)*expansion+0.2, 0.08*(1.0-t)+0.015, 0, color, (1.0-t)*0.75);
		m.pop();
		m.push(0, 0.5 + t*2, 0);
		m.torus((radius+3)*expansion+0.2, 0.03, 0, color, (1.0-t)*0.25);
		m.pop();
	}

	private static double ease(double value) {
		double t = clamp(value);
		return t*t*(3.0-2.0*t);
	}
	private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
	private static double fract(double value) { return value-Math.floor(value); }
	private static double mix(double a, double b, double t) { return a+(b-a)*t; }

	static final class Mesh {
		private final Sink sink;
		private final float opacity;
		private final ArrayDeque<Matrix4f> matrices = new ArrayDeque<>();
		private Matrix4f matrix = new Matrix4f();
		private final Vector3f point = new Vector3f();

		private Mesh(Sink sink, float opacity) { this.sink=sink; this.opacity=opacity; }
		void push(double x, double y, double z) {
			matrices.push(matrix);
			matrix = new Matrix4f(matrix).translate((float)x, (float)y, (float)z);
		}
		void pop() { matrix=matrices.pop(); }
		void rotate(double x, double y, double z) { matrix.rotateXYZ((float)x, (float)y, (float)z); }
		private void scale(double value) { matrix.scale((float)value); }
		private void vertex(Material material, double x, double y, double z, int rgb, double alpha) {
			matrix.transformPosition(point.set((float)x, (float)y, (float)z));
			int a=(int)Math.round(clamp(alpha*opacity)*255.0);
			sink.vertex(material, point.x, point.y, point.z, (a<<24)|(rgb&0xFFFFFF));
		}
		private void quad(Material material, double[] a, double[] b, double[] c, double[] d, int rgb, double alpha) {
			vertex(material,a[0],a[1],a[2],rgb,alpha); vertex(material,b[0],b[1],b[2],rgb,alpha);
			vertex(material,c[0],c[1],c[2],rgb,alpha); vertex(material,d[0],d[1],d[2],rgb,alpha);
		}
		private void gradientQuad(double ax,double ay,double az,double bx,double by,double bz,
			double cx,double cy,double cz,double dx,double dy,double dz,int rgb,double bottom,double top) {
			vertex(Material.GLOW,ax,ay,az,rgb,bottom); vertex(Material.GLOW,bx,by,bz,rgb,bottom);
			vertex(Material.GLOW,cx,cy,cz,rgb,top); vertex(Material.GLOW,dx,dy,dz,rgb,top);
		}
		void torus(double radius, double thickness, double rotation, int rgb, double alpha) {
			for (int i=0;i<72;i++) {
				double a=i*TAU/72+rotation, b=(i+1)*TAU/72+rotation;
				for (int j=0;j<4;j++) {
					double u=j*TAU/4, v=(j+1)*TAU/4;
					quad(Material.GLOW,torusPoint(radius,thickness,a,u),torusPoint(radius,thickness,b,u),
						torusPoint(radius,thickness,b,v),torusPoint(radius,thickness,a,v),rgb,alpha);
				}
			}
		}
		private double[] torusPoint(double r,double tube,double a,double b) {
			double radius=r+Math.cos(b)*tube;
			return new double[] {Math.cos(a)*radius,Math.sin(b)*tube,Math.sin(a)*radius};
		}
		private void cone(double top,double bottom,double height,int rgb,double alpha) {
			cone(top,bottom,height,rgb,alpha,Material.GLOW);
		}
		private void cone(double top,double bottom,double height,int rgb,double alpha,Material material) {
			for(int i=0;i<48;i++) {
				double a=i*TAU/48,b=(i+1)*TAU/48;
				quad(material,new double[]{Math.cos(a)*top,0,Math.sin(a)*top},
					new double[]{Math.cos(b)*top,0,Math.sin(b)*top},
					new double[]{Math.cos(b)*bottom,height,Math.sin(b)*bottom},
					new double[]{Math.cos(a)*bottom,height,Math.sin(a)*bottom},rgb,alpha);
			}
		}
		private void lathe(double[] radii,double[] heights,int segments,int rgb) {
			for(int ring=0;ring<radii.length-1;ring++) {
				for(int i=0;i<segments;i++) {
					double a=i*TAU/segments,b=(i+1)*TAU/segments;
					double lighting=0.55+0.25*Math.cos(a-0.8)+0.12*(1.0-ring/(double)radii.length);
					quad(Material.SOLID,new double[]{Math.cos(a)*radii[ring],heights[ring],Math.sin(a)*radii[ring]},
						new double[]{Math.cos(b)*radii[ring],heights[ring],Math.sin(b)*radii[ring]},
						new double[]{Math.cos(b)*radii[ring+1],heights[ring+1],Math.sin(b)*radii[ring+1]},
						new double[]{Math.cos(a)*radii[ring+1],heights[ring+1],Math.sin(a)*radii[ring+1]},shade(rgb,lighting),1.0);
				}
			}
		}
		private void sphere(double radius, int rgb, double alpha) {
			for (int ring = 0; ring < 12; ring++) {
				double a = -Math.PI/2 + ring*Math.PI/12, b = -Math.PI/2 + (ring+1)*Math.PI/12;
				for (int i = 0; i < 32; i++) {
					double u = i*TAU/32, v = (i+1)*TAU/32;
					quad(Material.GLOW, spherePoint(radius,a,u), spherePoint(radius,a,v), spherePoint(radius,b,v), spherePoint(radius,b,u), rgb, alpha);
				}
			}
		}
		private double[] spherePoint(double radius, double latitude, double longitude) {
			return new double[] {radius*Math.cos(latitude)*Math.cos(longitude), radius*Math.sin(latitude), radius*Math.cos(latitude)*Math.sin(longitude)};
		}
		void tube(double ax,double ay,double az,double bx,double by,double bz,double r,int rgb,Material mat,double alpha) {
			Vector3f direction=new Vector3f((float)(bx-ax),(float)(by-ay),(float)(bz-az));
			if(direction.lengthSquared()<0.000001f) return;
			direction.normalize();
			Vector3f side=new Vector3f(direction).cross(Math.abs(direction.y)<0.9?new Vector3f(0,1,0):new Vector3f(1,0,0)).normalize().mul((float)r);
			Vector3f up=new Vector3f(direction).cross(side).normalize().mul((float)r);
			for(int i=0;i<4;i++) {
				double a=i*TAU/4,b=(i+1)*TAU/4;
				double[] s={side.x*Math.cos(a)+up.x*Math.sin(a),side.y*Math.cos(a)+up.y*Math.sin(a),side.z*Math.cos(a)+up.z*Math.sin(a)};
				double[] t={side.x*Math.cos(b)+up.x*Math.sin(b),side.y*Math.cos(b)+up.y*Math.sin(b),side.z*Math.cos(b)+up.z*Math.sin(b)};
				quad(mat,new double[]{ax+s[0],ay+s[1],az+s[2]},new double[]{bx+s[0],by+s[1],bz+s[2]},
					new double[]{bx+t[0],by+t[1],bz+t[2]},new double[]{ax+t[0],ay+t[1],az+t[2]},rgb,alpha);
			}
		}
		void box(double x,double y,double z,int rgb,Material mat,double alpha) {
			double[][] p={{-x,-y,-z},{x,-y,-z},{x,y,-z},{-x,y,-z},{-x,-y,z},{x,-y,z},{x,y,z},{-x,y,z}};
			int[][] faces={{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}};
			for(int i=0;i<faces.length;i++) {
				int[] q=faces[i];
				quad(mat,p[q[0]],p[q[1]],p[q[2]],p[q[3]],shade(rgb,0.65+(i%3)*0.15),alpha);
			}
		}
		private void octahedron(double x,double y,double z,int rgb,Material mat,double alpha) {
			double[][] p={{x,0,0},{0,0,z},{-x,0,0},{0,0,-z}};
			for(int i=0;i<4;i++) {
				quad(mat,new double[]{0,y,0},p[i],p[(i+1)%4],p[(i+1)%4],shade(rgb,0.7+i*0.08),alpha);
				quad(mat,new double[]{0,-y,0},p[(i+1)%4],p[i],p[i],shade(rgb,0.45+i*0.06),alpha);
			}
		}
		private void rectangle(double x,double z,double r,int rgb,double alpha) {
			tube(-x,0,-z,x,0,-z,r,rgb,Material.GLOW,alpha); tube(x,0,-z,x,0,z,r,rgb,Material.GLOW,alpha);
			tube(x,0,z,-x,0,z,r,rgb,Material.GLOW,alpha); tube(-x,0,z,-x,0,-z,r,rgb,Material.GLOW,alpha);
		}
		private int shade(int rgb,double multiplier) {
			return ((int)(((rgb>>16)&255)*multiplier)<<16)|((int)(((rgb>>8)&255)*multiplier)<<8)|(int)((rgb&255)*multiplier);
		}
	}
}
