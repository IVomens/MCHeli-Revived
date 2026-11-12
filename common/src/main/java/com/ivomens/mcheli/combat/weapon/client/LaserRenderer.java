package com.ivomens.mcheli.combat.weapon.client;

import com.ivomens.mcheli.client.render.LegacyGL;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side laser beam renderer for {@link LaserWeapon}.
 *
 * <p>Beams are drawn as two view-facing quads (a wide additive glow + a bright core) into an additive,
 * depth-test-free colour render type after particles.</p>
 *
 * <p>Driven by the client tick and level render hooks.</p>
 */
public class LaserRenderer {

    private static final List<Beam> BEAMS = new ArrayList<>();

    public static void addBeam(Vec3 start, Vec3 end, int argb, float width, int lifeTicks, boolean pulsate, double renderStartDist) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        synchronized (BEAMS) {
            BEAMS.add(new Beam(start, end, r, g, b, a, width, pulsate, lifeTicks, renderStartDist));
        }
    }

    public static void onClientTick() {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        synchronized (BEAMS) {
            Iterator<Beam> it = BEAMS.iterator();
            while (it.hasNext()) {
                if (--it.next().lifeTicks <= 0) {
                    it.remove();
                }
            }
        }
    }

    /** Draws the beams; called once particles have been rendered. */
    public static void renderAfterParticles(Camera camera, float partialTicks, @Nullable PoseStack levelPoseStack) {
        synchronized (BEAMS) {
            if (BEAMS.isEmpty()) {
                return;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Vector3f lookF = camera.getLookVector();
        Vec3 look = new Vec3(lookF.x(), lookF.y(), lookF.z());
        long worldTime = mc.level.getGameTime();
        Vec3 cam = camera.getPosition();

        PoseStack poseStack = levelPoseStack != null ? levelPoseStack : new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        LegacyGL.begin(poseStack, buffers, LightTexture.FULL_BRIGHT);
        try {
            VertexConsumer vc = LegacyGL.colorBuffer(true, true);
            synchronized (BEAMS) {
                for (Beam beam : BEAMS) {
                    renderBeam(beam, vc, look, worldTime, partialTicks);
                }
            }
        } finally {
            LegacyGL.end();
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static void quad(VertexConsumer vc, double sx, double sy, double sz, double ex, double ey, double ez,
                             double hx, double hy, double hz, float r, float g, float b, float a) {
        LegacyGL.vertexColor(vc, (float) (sx - hx), (float) (sy - hy), (float) (sz - hz), r, g, b, a);
        LegacyGL.vertexColor(vc, (float) (sx + hx), (float) (sy + hy), (float) (sz + hz), r, g, b, a);
        LegacyGL.vertexColor(vc, (float) (ex + hx), (float) (ey + hy), (float) (ez + hz), r, g, b, a);
        LegacyGL.vertexColor(vc, (float) (ex - hx), (float) (ey - hy), (float) (ez - hz), r, g, b, a);
    }

    private static void renderBeam(Beam beam, VertexConsumer vc, Vec3 look, long worldTime, float partialTicks) {
        Vec3 start = visibleStart(beam);
        if (start == null) {
            return;
        }
        double sx = start.x;
        double sy = start.y;
        double sz = start.z;
        final double ex = beam.end.x;
        final double ey = beam.end.y;
        final double ez = beam.end.z;
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float width = beam.width;
        if (beam.pulsate) {
            width *= (float) (0.7 + 0.3 * Math.sin((worldTime + partialTicks) * 0.4));
        }

        Vec3 across = acrossView(dx / length, dy / length, dz / length, look);
        if (across == null) {
            return;
        }
        double halfWidth = width * 0.5;
        double hwX = across.x * halfWidth;
        double hwY = across.y * halfWidth;
        double hwZ = across.z * halfWidth;

        // wide, faint glow in a brightened colour
        float gr = Math.min(beam.r * 1.5F, 1.0F);
        float gg = Math.min(beam.g * 1.5F, 1.0F);
        float gb = Math.min(beam.b * 1.5F, 1.0F);
        float ga = beam.a * 0.4F;
        quad(vc, sx, sy, sz, ex, ey, ez, hwX * 2.0, hwY * 2.0, hwZ * 2.0, gr, gg, gb, ga);

        // bright core
        quad(vc, sx, sy, sz, ex, ey, ez, hwX, hwY, hwZ, beam.r, beam.g, beam.b, beam.a);
    }

    /**
     * Where the drawn beam starts: its start, moved forward past the part not drawn (e.g. inside the
     * turret). Null when nothing of the beam is left to draw.
     */
    @Nullable
    private static Vec3 visibleStart(Beam beam) {
        double sx = beam.start.x;
        double sy = beam.start.y;
        double sz = beam.start.z;
        final double ex = beam.end.x;
        final double ey = beam.end.y;
        final double ez = beam.end.z;

        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-8) {
            return null;
        }

        if (beam.renderStartDist > 0) {
            double startDist = Math.min(beam.renderStartDist, length);
            if (startDist > 0) {
                double ratio = startDist / length;
                sx += dx * ratio;
                sy += dy * ratio;
                sz += dz * ratio;
                dx = ex - sx;
                dy = ey - sy;
                dz = ez - sz;
                length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1.0E-8) {
                    return null;
                }
            }
        }
        return new Vec3(sx, sy, sz);
    }

    /**
     * The unit vector across the beam in the screen plane, cross(beam direction, view direction), so the
     * quads face the camera; null when there is none.
     */
    @Nullable
    private static Vec3 acrossView(double dirX, double dirY, double dirZ, Vec3 look) {
        double crossX = dirY * look.z - dirZ * look.y;
        double crossY = dirZ * look.x - dirX * look.z;
        double crossZ = dirX * look.y - dirY * look.x;
        double crossLen = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
        if (crossLen < 1.0E-8) {
            crossX = -dirZ;
            crossY = 0.0;
            crossZ = dirX;
            crossLen = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
            if (crossLen < 1.0E-8) {
                return null;
            }
        }
        return new Vec3(crossX / crossLen, crossY / crossLen, crossZ / crossLen);
    }

    public static class Beam {
        final Vec3 start;
        final Vec3 end;
        final float r;
        final float g;
        final float b;
        final float a;
        final float width;
        final boolean pulsate;
        final double renderStartDist;
        int lifeTicks;

        Beam(Vec3 start, Vec3 end, float r, float g, float b, float a, float width, boolean pulsate, int lifeTicks, double renderStartDist) {
            this.start = start;
            this.end = end;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.width = width;
            this.pulsate = pulsate;
            this.lifeTicks = lifeTicks;
            this.renderStartDist = renderStartDist;
        }
    }
}
