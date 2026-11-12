package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.Blade;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.Parts;
import com.ivomens.mcheli.aircraft.Rotor;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Detail;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.LandingGear;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.PartPoses;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.PlaneParts;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Swing;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.TrackOffsets;
import com.ivomens.mcheli.vehicle.component.RunningGearComponent;
import com.ivomens.mcheli.vehicle.component.WeaponBayDoor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Reads the animation state of a vehicle's parts into {@link PartPoses}, interpolated for the frame. */
final class PartPoseCapture {

    /** Crawler tracks of vehicles farther than this are drawn as the static mesh. */
    private static final double STATIC_TRACK_DISTANCE = 256.0;
    /** Below this speed on both horizontal axes a vehicle counts as parked. */
    private static final double MOVING_SPEED = 0.01;
    private static final float RIGHT_ANGLE = 90.0F;

    private PartPoseCapture() {
    }

    static PartPoses capture(AircraftEntity ac, VehicleSpec info, Detail detail, float tickTime) {
        RunningGearComponent gear = ac.runningGear();
        AircraftCamera camera = ac.cameras().camera();
        float cameraYaw = 0.0F;
        float cameraPitch = 0.0F;
        if (info.parts().havePartCamera()) {
            cameraYaw = Mth.lerp(tickTime, camera.prevPartRotationYaw(), camera.partRotationYaw()) - ac.getYRot();
            cameraPitch = Mth.lerp(tickTime, camera.prevPartRotationPitch(), camera.partRotationPitch()) - ac.getXRot();
        }
        return new PartPoses(intactEra(ac), WeaponPoseCapture.capture(ac, info, tickTime), spinningParts(ac, tickTime),
                info.parts().haveHatch() ? swing(ac.parts().hatch(), ac.parts().hatchRotation(),
                        ac.parts().prevHatchRotation(), tickTime) : null,
                info.parts().haveCanopy() ? swing(ac.parts().canopy(), ac.parts().canopyRotation(),
                        ac.parts().prevCanopyRotation(), tickTime) : null,
                Angles.wrap(new float[]{gear.trackRoller(0, tickTime), gear.trackRoller(1, tickTime)}),
                new TrackOffsets(gear.trackOffset(0, tickTime), gear.trackOffset(1, tickTime)),
                usesStaticTracks(ac, detail), gear.wheelSteer(tickTime), gear.wheelRoll(tickTime),
                ac.searchLight().hatch(tickTime),
                Mth.lerp(tickTime, (float) ac.engine().previousThrottle(), (float) ac.engine().throttle()),
                cameraYaw, cameraPitch, landingGear(ac, info, tickTime), weaponBays(ac, info, tickTime),
                rotors(ac, tickTime), planeParts(ac, tickTime), GroundPartCapture.capture(ac, tickTime));
    }

    /** Parked or far away vehicles draw the static track mesh with the body instead of animated links. */
    private static boolean usesStaticTracks(AircraftEntity ac, Detail detail) {
        Vec3 motion = ac.getDeltaMovement();
        boolean parked = Math.abs(motion.x) <= MOVING_SPEED && Math.abs(motion.z) <= MOVING_SPEED;
        return parked || detail.cameraDistanceSq() > STATIC_TRACK_DISTANCE * STATIC_TRACK_DISTANCE;
    }

    /** Reactive armour tiles, in the order of the ERA boxes in {@code extraBoundingBox}. */
    private static List<Boolean> intactEra(AircraftEntity ac) {
        List<Boolean> intact = null;
        for (PartBoundingBox box : ac.partBoxes().boxes()) {
            if (box.isERA()) {
                if (intact == null) {
                    intact = new ArrayList<>();
                }
                intact.add(box.eraActive());
            }
        }
        return intact == null ? List.of() : List.copyOf(intact);
    }

    private static Angles spinningParts(AircraftEntity ac, float tickTime) {
        RunningGearComponent gear = ac.runningGear();
        if (!gear.hasSpinningParts()) {
            return Angles.NONE;
        }
        float[] angles = new float[gear.spinningPartCount()];
        for (int i = 0; i < angles.length; i++) {
            angles[i] = gear.spinningPart(i, tickTime);
        }
        return Angles.wrap(angles);
    }

    @Nullable
    private static Swing swing(@Nullable Parts part, float rotation, float prevRotation, float tickTime) {
        if (part == null) {
            return null;
        }
        return new Swing(Mth.lerp(tickTime, prevRotation, rotation),
                Mth.lerp(tickTime, part.prevRotation() / part.rotationMax(), part.rotation() / part.rotationMax()));
    }

    /** Gear doors open three times as fast as the gear and stay open: the sine of twice the gear angle. */
    @Nullable
    private static LandingGear landingGear(AircraftEntity ac, VehicleSpec info, float tickTime) {
        if (!info.parts().haveLandingGear() || ac.parts().landingGear() == null) {
            return null;
        }
        float rot = ac.parts().landingGearRotation();
        float prevRot = ac.parts().prevLandingGearRotation();
        float gear = Mth.lerp(tickTime, prevRot, rot);
        float reversed = Mth.lerp(tickTime, RIGHT_ANGLE - prevRot, RIGHT_ANGLE - rot);
        float door = Math.min(RIGHT_ANGLE * Mth.sin(gear * 2.0F * (float) Math.PI / 180.0F) * 3.0F, RIGHT_ANGLE);
        return new LandingGear(gear, reversed, door, rot / RIGHT_ANGLE);
    }

    private static List<Swing> weaponBays(AircraftEntity ac, VehicleSpec info, float tickTime) {
        if (info.parts().weaponBays().isEmpty()) {
            return List.of();
        }
        List<Swing> bays = new ArrayList<>(info.parts().weaponBays().size());
        for (int i = 0; i < info.parts().weaponBays().size(); i++) {
            WeaponBayDoor door = ac.parts().weaponBay(i);
            bays.add(new Swing(Mth.lerp(tickTime, door.prevAngle(), door.angle()),
                    Mth.lerp(tickTime, door.prevAngle() / RIGHT_ANGLE, door.angle() / RIGHT_ANGLE)));
        }
        return List.copyOf(bays);
    }

    /** Helicopter blades: each blade's angle, unwrapped across the 360 degree seam. */
    private static List<Angles> rotors(AircraftEntity ac, float tickTime) {
        if (!(ac instanceof HelicopterEntity heli) || !(heli.helicopterSpec() instanceof VehicleSpec info)) {
            return List.of();
        }
        Rotor[] rotors = heli.blades().rotors();
        int count = Math.min(rotors.length, info.kindParts().heliRotors().size());
        List<Angles> angles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Blade[] blades = rotors[i].blades();
            float[] blade = new float[blades.length];
            for (int b = 0; b < blades.length; b++) {
                float rot = blades[b].getRotation();
                float prevRot = blades[b].getPrevRotation();
                if (rot - prevRot < -180.0F) {
                    prevRot -= 360.0F;
                } else if (prevRot - rot < -180.0F) {
                    prevRot += 360.0F;
                }
                blade[b] = prevRot + (rot - prevRot) * tickTime;
            }
            angles.add(Angles.wrap(blade));
        }
        return List.copyOf(angles);
    }

    private static PlaneParts planeParts(AircraftEntity ac, float tickTime) {
        if (!(ac instanceof AbstractPlaneEntity plane) || !(plane.planeSpec() instanceof VehicleSpec info)) {
            return PlaneParts.NONE;
        }
        boolean wings = info.kindParts().haveWing() && plane.wings().isFitted();
        boolean rotors = info.kindParts().haveRotor() && plane.nozzles().isFitted();
        return new PlaneParts(info.kindParts().haveNozzle() && plane.nozzles().isFitted(), wings, rotors,
                Mth.lerp(tickTime, plane.nozzles().prevAngle(), plane.nozzles().angle()),
                wings ? Mth.lerp(tickTime, plane.wings().prevAngle(), plane.wings().angle()) : 0.0F,
                rotors ? Mth.lerp(tickTime, plane.propeller().prevAngle(), plane.propeller().angle()) : 0.0F);
    }
}
