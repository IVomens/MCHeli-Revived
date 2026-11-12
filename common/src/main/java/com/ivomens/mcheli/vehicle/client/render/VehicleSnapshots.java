package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.client.VehicleModelRegistry;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Detail;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.HullPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Tint;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds {@link VehicleRenderSnapshot}s from vehicle entities. Capturing only reads the entity, so
 * it is safe on Flywheel's frame workers as well as on the render thread; the few per-frame state
 * changes the drawing used to make live in {@link VehicleFrameUpdates}.
 */
public final class VehicleSnapshots {

    private static final float BODY_SHADE = 0.75F;
    private static final float DESTROYED_SHADE = 0.15F;
    private static final Tint DESTROYED = new Tint(DESTROYED_SHADE, DESTROYED_SHADE, DESTROYED_SHADE, 1.0F);
    /** The camera is inside the model this close, where a level of detail makes no sense. */
    private static final double FULL_DETAIL_DISTANCE = 1.0;
    /** {@link #pixelsAtOneBlock()} as last computed on the render thread, as float bits. */
    private static final AtomicInteger LAST_PIXELS_AT_ONE_BLOCK =
            new AtomicInteger(Float.floatToIntBits(Float.POSITIVE_INFINITY));

    private VehicleSnapshots() {
    }

    /**
     * Where a frame is drawn from.
     *
     * @param partialTick      how far the frame is between the last tick and the next
     * @param camera           the camera position, for the level of detail
     * @param pixelsAtOneBlock how many pixels a block covers one block from the camera; infinite for
     *                         full detail (see {@link #pixelsAtOneBlock()})
     */
    public record View(float partialTick, Vec3 camera, float pixelsAtOneBlock) {
    }

    /**
     * The snapshot of {@code ac} for a frame, or null when its definition is not known yet.
     *
     * @param texture       the texture the vehicle wears (see {@link VehicleTextures})
     * @param fallbackLight the light at the entity, used while the vehicle has no light of its own
     */
    @Nullable
    public static VehicleRenderSnapshot capture(AircraftEntity ac, View view, ResourceLocation texture,
                                                int fallbackLight) {
        VehicleSpec info = ac.spec();
        if (info == null) {
            return null;
        }
        float partialTick = view.partialTick();
        HullPose hull = hullPose(ac, info, partialTick);
        double dx = hull.x() - view.camera().x;
        double dy = hull.y() - view.camera().y;
        double dz = hull.z() - view.camera().z;
        Detail detail = detail(dx * dx + dy * dy + dz * dz, view.pixelsAtOneBlock());
        // the vehicle keeps its own smoothed light, full bright while a search light is on
        int light = ac.lighting().packedLight();
        return new VehicleRenderSnapshot(info, VehicleModelRegistry.of(info), hull, texture, tint(ac, partialTick),
                light != 0 ? light : fallbackLight, detail, PartPoseCapture.capture(ac, info, detail, partialTick));
    }

    private static HullPose hullPose(AircraftEntity ac, VehicleSpec info, float partialTick) {
        // slerp the orientation instead of lerping each Euler angle: shortest path, no wrap-around jumps
        float[] ypr = Rotations.slerpYawPitchRoll(ac.yRotO, ac.xRotO, ac.rollAngle().previous(), ac.getYRot(), ac.getXRot(),
                ac.roll(), partialTick);
        return new HullPose(Mth.lerp(partialTick, ac.xOld, ac.getX()), Mth.lerp(partialTick, ac.yOld, ac.getY()),
                Mth.lerp(partialTick, ac.zOld, ac.getZ()), ypr[0], ypr[1], ypr[2], info.kind() != VehicleKind.VEHICLE);
    }

    /** Wrecks are dark, the iron curtain shimmers red, otherwise the usual slightly shaded body. */
    private static Tint tint(AircraftEntity ac, float partialTick) {
        if (ac.isDestroyed()) {
            return DESTROYED;
        }
        if (ac.countermeasures().ironCurtain().isRunning()) {
            float factor = ac.countermeasures().ironCurtain().shimmer((float) Math.sin(partialTick * Math.PI / 2));
            return new Tint(0.8F * factor, 0.4F * factor, 0.4F * factor, 1.0F);
        }
        return new Tint(BODY_SHADE, BODY_SHADE, BODY_SHADE, (float) McheliConfig.debug().textureAlpha());
    }

    private static Detail detail(double distanceSq, float pixelsAtOneBlock) {
        double distance = Math.sqrt(distanceSq);
        if (pixelsAtOneBlock == Float.POSITIVE_INFINITY || distance < FULL_DETAIL_DISTANCE) {
            return new Detail(distanceSq, Float.POSITIVE_INFINITY);
        }
        return new Detail(distanceSq, (float) (pixelsAtOneBlock / distance));
    }

    /**
     * How many pixels one block covers one block in front of the camera, from the current projection
     * and window; infinite for orthographic projections (GUI previews), which get full detail. Render
     * thread only.
     */
    public static float pixelsAtOneBlock() {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        // perspective projections have m23 = -1
        if (projection.m23() == 0.0F) {
            return Float.POSITIVE_INFINITY;
        }
        float pixels = projection.m11() * Minecraft.getInstance().getWindow().getHeight() * 0.5F;
        LAST_PIXELS_AT_ONE_BLOCK.set(Float.floatToIntBits(pixels));
        return pixels;
    }

    /**
     * {@link #pixelsAtOneBlock()} of the world as the render thread last drew it, for Flywheel's frame
     * workers, which may not touch the render system.
     */
    public static float lastPixelsAtOneBlock() {
        return Float.intBitsToFloat(LAST_PIXELS_AT_ONE_BLOCK.get());
    }
}
