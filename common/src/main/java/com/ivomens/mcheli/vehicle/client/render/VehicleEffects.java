package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import com.ivomens.mcheli.plane.ExhaustFlames;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.vehicle.client.ExhaustFlameTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a vehicle shows besides its model in one frame: the repelling ropes, the search light cones,
 * the afterburner flames and, in test mode, the crawler track outlines. Effects are additive or line
 * geometry that instancing gains nothing on, so both backends leave them to
 * {@link VehicleEffectsRenderer}.
 *
 * @param repelling      whether the repelling ropes are down
 * @param ropeLength     how far they hang
 * @param searchLights   the search light aim, or null when the lights are off
 * @param flames         the afterburner flames, or null when they do not burn
 * @param trackOutlines  whether to draw the crawler track outlines (test mode, animated tracks)
 */
public record VehicleEffects(boolean repelling, float ropeLength, @Nullable SearchLights searchLights,
                             @Nullable Flames flames, boolean trackOutlines) {

    private static final String TEX_EXHAUST_DIR = "textures/exhaustflames/";
    /** Flip-book frame textures per texture prefix; render thread only. */
    private static final Map<String, ResourceLocation[]> FLAME_FRAMES = new HashMap<>();

    /**
     * The search light aim: the steerable lights point where the operator looks, the fixed ones
     * follow the airframe and, if they steer, the front wheels.
     */
    public record SearchLights(float aimYaw, float aimPitch, float hullYaw, float hullPitch, float steering) {
    }

    /**
     * Afterburner flames, one texture frame per flame.
     *
     * @param throttle  the throttle, stretching the flames
     * @param yawFactor how far the yaw input swings them sideways, -1 to 1
     */
    public record Flames(float throttle, float yawFactor, List<ResourceLocation> textures) {
    }

    /**
     * Captures the effects of {@code ac}. Render thread only: like the original renderer it aims the
     * steerable search lights at the operator's look and steps the flame flip-books, once per drawn
     * frame.
     */
    public static VehicleEffects capture(AircraftEntity ac, VehicleSpec info, VehicleRenderSnapshot snapshot,
                                         float tickTime, ThermalVision thermal) {
        boolean outlines = McheliConfig.debug().testMode() && !snapshot.parts().staticTracks()
                && !info.parts().crawlerTracks().isEmpty();
        boolean repelling = ac.ropes().isRepelling();
        return new VehicleEffects(repelling, repelling ? ac.ropes().ropeLength() : 0.0F,
                searchLights(ac, tickTime), flames(ac, tickTime, thermal), outlines);
    }

    @Nullable
    private static SearchLights searchLights(AircraftEntity ac, float tickTime) {
        if (!ac.searchLight().isFitted() || !ac.searchLight().isOn()) {
            return null;
        }
        VehicleFrameUpdates.aimSearchLights(ac);
        return new SearchLights(ac.searchLight().aimYaw(), ac.searchLight().aimPitch(), ac.getYRot(), ac.getXRot(),
                ac.runningGear().wheelSteer(tickTime));
    }

    /** Hidden at idle and in VTOL mode. */
    @Nullable
    private static Flames flames(AircraftEntity ac, float tickTime, ThermalVision thermal) {
        if (!(ac instanceof PlaneEntity plane) || !(plane.planeSpec() instanceof VehicleSpec info)
                || info.kindParts().exhaustFlames().isEmpty()) {
            return null;
        }
        int count = info.kindParts().exhaustFlames().size();
        ExhaustFlames.Animation animation = plane.exhaustFlames().animation(count);
        float throttle = Mth.clamp((float) plane.engine().throttle(), 0.0F, 1.0F);
        if (throttle == 0.0F || plane.nozzles().mode() != 0) {
            return null;
        }
        float yawFactor = Mth.clamp(Mth.lerp(tickTime, plane.exhaustFlames().prevLean(),
                plane.exhaustFlames().lean()), -1.0F, 1.0F);
        ResourceLocation[] textures = new ResourceLocation[count];
        for (int i = 0; i < count; i++) {
            VehicleParts.ExhaustFlame flame = info.kindParts().exhaustFlames().get(i);
            int frame = VehicleFrameUpdates.advanceFlameFrame(animation, i, flame);
            textures[i] = thermal.remap(flameTexture(flame.texturePrefix, frame));
        }
        return new Flames(throttle, yawFactor, List.of(textures));
    }

    private static ResourceLocation flameTexture(String prefix, int frame) {
        ResourceLocation[] frames = FLAME_FRAMES.get(prefix);
        if (frames == null || frame >= frames.length) {
            int size = Math.max(frame + 1, ExhaustFlameTextures.frames(prefix));
            frames = frames == null ? new ResourceLocation[size] : Arrays.copyOf(frames, size);
            for (int i = 0; i < size; i++) {
                if (frames[i] == null) {
                    frames[i] = McheliEntityRenderer.texture(TEX_EXHAUST_DIR, prefix + i);
                }
            }
            FLAME_FRAMES.put(prefix, frames);
        }
        return frames[frame];
    }
}
