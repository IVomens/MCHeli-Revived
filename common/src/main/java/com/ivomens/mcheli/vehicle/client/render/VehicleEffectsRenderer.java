package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.client.render.RenderLib;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.client.render.VehicleEffects.Flames;
import com.ivomens.mcheli.vehicle.client.render.VehicleEffects.SearchLights;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a vehicle's {@link VehicleEffects} into the open {@link LegacyGL} frame, posed at the
 * vehicle's origin. Effects are always drawn this way, whichever backend draws the model.
 */
public final class VehicleEffectsRenderer {

    private static final String DEFAULT_FLAME_MODEL = "Exhaustflame";
    private static final int SEARCH_LIGHT_SEGMENTS = 24;
    private static final float[] SEARCH_LIGHT_SIN = new float[SEARCH_LIGHT_SEGMENTS + 1];
    private static final float[] SEARCH_LIGHT_COS = new float[SEARCH_LIGHT_SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEARCH_LIGHT_SEGMENTS; i++) {
            float angle = (float) (15.0 * i / 180.0 * Math.PI);
            SEARCH_LIGHT_SIN[i] = Mth.sin(angle);
            SEARCH_LIGHT_COS[i] = Mth.cos(angle);
        }
    }

    private VehicleEffectsRenderer() {
    }

    public static void render(VehicleRenderSnapshot snapshot, VehicleEffects effects) {
        VehicleSpec info = snapshot.info();
        LegacyGL.pushMatrix();
        try {
            VehicleModelPoser.applyHull(ImmediateVehicleRenderer.LegacyGLPartSink.INSTANCE, snapshot.hull());
            if (effects.repelling()) {
                renderRopes(info, effects.ropeLength());
            }
            if (effects.trackOutlines()) {
                for (VehicleParts.CrawlerTrack track : info.parts().crawlerTracks()) {
                    renderTrackOutline(track);
                }
            }
            if (effects.flames() != null && info.kind() == VehicleKind.PLANE) {
                renderExhaustFlames(info, effects.flames());
            }
            if (effects.searchLights() != null) {
                renderSearchLights(info, effects.searchLights());
            }
        } finally {
            LegacyGL.popMatrix();
        }
    }

    private static void renderRopes(VehicleSpec info, float length) {
        VertexConsumer lines = LegacyGL.lineBuffer(0.0F, false);
        for (VehicleParts.RepellingHook hook : info.crew().repellingHooks()) {
            Vec3 p = hook.pos();
            LegacyGL.line(lines, p.x, p.y, p.z, p.x, p.y + length, p.z, 0.0F, 0.0F, 0.0F, 1.0F);
        }
    }

    /** Test mode: the points a crawler track's links run along. */
    private static void renderTrackOutline(VehicleParts.CrawlerTrack track) {
        VertexConsumer consumer = LegacyGL.colorBuffer(false, false);
        float size = track.len * 0.05F;
        for (int i = 0; i < track.cx.length; i++) {
            float ratio = (float) i / track.cx.length;
            RenderLib.drawPoint(consumer, track.z, track.cx[i], track.cy[i], size, ratio, 80 / 255.0F, 1.0F - ratio, 1.0F);
        }
    }

    /**
     * Afterburner flames: a flip-book texture on a flame model, stretched with the throttle and
     * swung sideways with the yaw input.
     */
    private static void renderExhaustFlames(VehicleSpec info, Flames flames) {
        LegacyGL.pushAttrib();
        for (int i = 0; i < info.kindParts().exhaustFlames().size(); i++) {
            VehicleParts.ExhaustFlame flame = info.kindParts().exhaustFlames().get(i);
            LegacyGL.bindTexture(flames.textures().get(i));
            LegacyGL.disableLighting();
            LegacyGL.enableBlend();
            LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);

            float yaw = Mth.clamp(flames.yawFactor() * flame.degreeYaw, -flame.degreeYaw, flame.degreeYaw);
            LegacyGL.pushMatrix();
            LegacyGL.translate(flame.pos.x, flame.pos.y, flame.pos.z);
            LegacyGL.rotate(-yaw, (float) flame.rot.x, (float) flame.rot.y, (float) flame.rot.z);
            LegacyGL.scale(1.0F, 1.0F, flames.throttle());
            String model = flame.modelName != null && !flame.modelName.isEmpty() ? flame.modelName : DEFAULT_FLAME_MODEL;
            ModelCache.render("exhaustflames", model);
            LegacyGL.popMatrix();
        }
        LegacyGL.popAttrib();
    }

    /** Additive light cones, aimed by the gunner (or pilot) unless fixed to the airframe. */
    private static void renderSearchLights(VehicleSpec info, SearchLights aim) {
        for (VehicleParts.SearchLight light : info.parts().searchLights()) {
            LegacyGL.pushMatrix();
            LegacyGL.translate(light.pos.x, light.pos.y, light.pos.z);
            if (!light.fixDir) {
                LegacyGL.rotate(aim.aimYaw() - aim.hullYaw() + light.yaw, 0.0F, -1.0F, 0.0F);
                LegacyGL.rotate(aim.aimPitch() + 90.0F - aim.hullPitch() + light.pitch, 1.0F, 0.0F, 0.0F);
            } else {
                float steeringYaw = light.steering ? -aim.steering() * light.stRot : 0.0F;
                LegacyGL.rotate(light.yaw + steeringYaw, 0.0F, -1.0F, 0.0F);
                LegacyGL.rotate(90.0F + light.pitch, 1.0F, 0.0F, 0.0F);
            }
            renderLightCone(light);
            LegacyGL.popMatrix();
        }
    }

    /** A cone from the lamp along +Y; the triangle fan is emitted as degenerate quads. */
    private static void renderLightCone(VehicleParts.SearchLight light) {
        int start = light.colorStart;
        int end = light.colorEnd;
        float sr = channel(start, 16), sg = channel(start, 8), sb = channel(start, 0), sa = channel(start, 24);
        float er = channel(end, 16), eg = channel(end, 8), eb = channel(end, 0), ea = channel(end, 24);
        float height = light.height;
        float radius = light.width / 2.0F;
        VertexConsumer consumer = LegacyGL.colorBuffer(true, false);
        for (int i = 0; i < SEARCH_LIGHT_SEGMENTS; i++) {
            float x0 = SEARCH_LIGHT_SIN[i] * radius, z0 = SEARCH_LIGHT_COS[i] * radius;
            float x1 = SEARCH_LIGHT_SIN[i + 1] * radius, z1 = SEARCH_LIGHT_COS[i + 1] * radius;
            LegacyGL.vertexColor(consumer, 0.0F, 0.0F, 0.0F, sr, sg, sb, sa);
            LegacyGL.vertexColor(consumer, x0, height, z0, er, eg, eb, ea);
            LegacyGL.vertexColor(consumer, x1, height, z1, er, eg, eb, ea);
            LegacyGL.vertexColor(consumer, x1, height, z1, er, eg, eb, ea);
        }
    }

    private static float channel(int argb, int shift) {
        return (argb >> shift & 0xFF) / 255.0F;
    }
}
