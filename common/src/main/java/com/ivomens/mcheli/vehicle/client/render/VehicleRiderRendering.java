package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.math.Rotations;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * How riders are drawn on a vehicle: hidden ({@code HideRiders}) or tilted and scaled with the
 * airframe. Called from the living-entity render events.
 */
public final class VehicleRiderRendering {

    private VehicleRiderRendering() {
    }

    /** The aircraft an entity is riding, directly or through a seat; {@code null} if none. */
    @Nullable
    public static AircraftEntity getRiddenAircraft(@Nullable Entity rider) {
        if (rider == null) {
            return null;
        }
        Entity vehicle = rider.getVehicle();
        if (vehicle instanceof AircraftEntity ac) {
            return ac;
        }
        if (vehicle instanceof SeatEntity seat) {
            return seat.getParent();
        }
        return null;
    }

    /**
     * Whether a rider should not be drawn: only the pilot, and only when the aircraft definition
     * asks for it ({@code HideRiders}/{@code HideEntity}). Call from {@code RenderLivingEvent.Pre}
     * and cancel the event when true.
     */
    public static boolean shouldHideRider(Entity rider) {
        AircraftEntity ac = getRiddenAircraft(rider);
        if (ac == null || ac.spec() == null || !McheliConfig.render().enableModEntityRender()) {
            return false;
        }
        return ac.spec().file().render().hideRiders() && ac.isPilot(rider);
    }

    /**
     * Tilts a rider with the airframe (pitch/roll plus the info's entity pitch/roll) and applies the
     * info's rider scale. The pose stack must be at the rider's render origin, as in
     * {@code RenderLivingEvent.Pre}.
     *
     * @return true if a pose was pushed; the caller must then {@code popPose()} in the matching Post event
     */
    public static boolean pushRiderTransform(Entity rider, PoseStack poseStack, float partialTicks) {
        if (!McheliConfig.render().enableModEntityRender()) {
            return false;
        }
        AircraftEntity ac = getRiddenAircraft(rider);
        VehicleSpec info = ac == null ? null : ac.spec();
        if (info == null) {
            return false;
        }
        float[] ypr = Rotations.slerpYawPitchRoll(ac.yRotO, ac.xRotO, ac.rollAngle().previous(), ac.getYRot(), ac.getXRot(),
                ac.roll(), partialTicks);
        float yaw = ypr[0];
        float pitch = ypr[1] + info.file().render().modelPitch();
        float roll = ypr[2] + info.file().render().modelRoll();
        float width = info.file().render().modelWidth();
        float height = info.file().render().modelHeight();
        if (Math.abs(pitch) < 0.01F && Math.abs(roll) < 0.01F && width == 1.0F && height == 1.0F) {
            return false;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YN.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.scale(width, height, width);
        poseStack.mulPose(Axis.YN.rotationDegrees(-yaw));
        return true;
    }
}
