package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.BakedPart;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.LandingGear;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.PartPoses;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Swing;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.TrackOffsets;

import java.util.Arrays;
import java.util.List;

/** Lays out the moving parts every vehicle type can have: armour, weapons, gear, hatches, wheels, tracks. */
final class CommonPartPoser {

    /** Bare part names {@code ERA0, ERA1, ...}, so drawing does not build them every frame. */
    private static final String[] ERA_PART_NAMES = eraPartNames(64);

    private CommonPartPoser() {
    }

    static void pose(VehicleRenderSnapshot snapshot, PartDrawer drawer) {
        VehicleSpec info = snapshot.info();
        PartPoses parts = snapshot.parts();
        poseReactiveArmor(parts.intactEra(), drawer);
        WeaponPartPoser.pose(info, parts.weapons(), drawer);
        poseSpinningParts(info, parts.spinningParts(), drawer);
        poseSwinging(info.parts().hatches(), parts.hatch(), drawer);
        poseTrackRollers(info, parts.trackRollers(), drawer);
        if (!parts.staticTracks()) {
            poseCrawlerTracks(info, parts.trackOffsets(), drawer);
        }
        poseRotated(info.parts().steeringWheels(), parts.steering(), drawer);
        poseLightHatches(info, parts.lightHatch(), drawer);
        poseWheels(info, parts.steering(), parts.wheelRoll(), drawer);
        poseThrottles(info, parts.throttle(), drawer);
        poseCameras(info, parts.cameraYaw(), parts.cameraPitch(), drawer);
        poseLandingGear(info, parts.landingGear(), drawer);
        poseWeaponBays(info, parts.weaponBays(), drawer);
        poseSwinging(info.parts().canopies(), parts.canopy(), drawer);
    }

    private static String[] eraPartNames(int count) {
        String[] names = new String[count];
        Arrays.setAll(names, i -> "ERA" + i);
        return names;
    }

    private static String eraPartName(int index) {
        return index < ERA_PART_NAMES.length ? ERA_PART_NAMES[index] : "ERA" + index;
    }

    /**
     * Each intact reactive armour tile is its own body group ({@code $ERA0}, {@code $ERA1}, ...), in
     * the order of the ERA boxes in {@code extraBoundingBox}; a spent tile is not drawn.
     */
    private static void poseReactiveArmor(List<Boolean> intact, PartDrawer drawer) {
        BakedModelBase body = drawer.bakedBody();
        if (body == null) {
            return;
        }
        for (int i = 0; i < intact.size(); i++) {
            if (intact.get(i)) {
                BakedPart tile = body.findPrefixedPart(eraPartName(i));
                if (tile != null) {
                    drawer.sink().draw(tile);
                }
            }
        }
    }

    private static void poseSpinningParts(VehicleSpec info, Angles angles, PartDrawer drawer) {
        for (int i = 0; i < angles.size(); i++) {
            VehicleParts.RotPart part = info.parts().rotatingParts().get(i);
            if (drawer.isVisible(part)) {
                drawer.drawRotated(part, angles.get(i));
            }
        }
    }

    /** Hatches and canopies: each swings by its share of the common angle, or slides open. */
    private static void poseSwinging(List<? extends VehicleParts.DrawnPart> parts, Swing swing, PartDrawer drawer) {
        if (swing == null) {
            return;
        }
        for (VehicleParts.DrawnPart part : parts) {
            if (!drawer.isVisible(part)) {
                continue;
            }
            if (part instanceof VehicleParts.Hatch hatch) {
                drawer.drawSlidingOrRotated(hatch, hatch.isSlide, swing.slide(), swing.angle() * hatch.maxRotFactor);
            } else if (part instanceof VehicleParts.Canopy canopy) {
                drawer.drawSlidingOrRotated(canopy, canopy.isSlide, swing.slide(), swing.angle() * canopy.maxRotFactor);
            }
        }
    }

    private static void poseTrackRollers(VehicleSpec info, Angles bySide, PartDrawer drawer) {
        PartSink sink = drawer.sink();
        for (VehicleParts.TrackRoller roller : info.parts().trackRollers()) {
            if (!drawer.isVisible(roller)) {
                continue;
            }
            float rot = bySide.get(roller.side);
            if (rot == 0.0F) {
                drawer.draw(roller);
                continue;
            }
            sink.pushPose();
            sink.rotateAround(rot, 1.0F, 0.0F, 0.0F, roller.pos.x, roller.pos.y, roller.pos.z);
            drawer.draw(roller);
            sink.popPose();
        }
    }

    /** Animated crawler tracks: one tread link per path point, moved along the path by the track offset. */
    static void poseCrawlerTracks(VehicleSpec info, TrackOffsets offsets, PartDrawer drawer) {
        PartSink sink = drawer.sink();
        for (VehicleParts.CrawlerTrack track : info.parts().crawlerTracks()) {
            if (!drawer.isVisible(track)) {
                continue;
            }
            double progress = offsets.side(track.side);
            int pointCount = track.lp.size() - 1;
            for (int i = 0; i < pointCount; i++) {
                VehicleParts.CrawlerTrackPrm current = track.lp.get(i);
                VehicleParts.CrawlerTrackPrm next = track.lp.get((i + 1) % pointCount);
                double nextRotation = next.r;
                if (nextRotation - current.r < -180.0) {
                    nextRotation += 360.0;
                }
                if (nextRotation - current.r > 180.0) {
                    nextRotation -= 360.0;
                }
                sink.pushPose();
                sink.translate(0.0, current.x + (next.x - current.x) * progress,
                        current.y + (next.y - current.y) * progress);
                sink.rotate((float) (current.r + (nextRotation - current.r) * progress), -1.0F, 0.0F, 0.0F);
                drawer.draw(track);
                sink.popPose();
            }
        }
    }

    /** Steering wheels: turned by the steering angle in their own direction. */
    private static void poseRotated(List<VehicleParts.PartWheel> wheels, float steering, PartDrawer drawer) {
        for (VehicleParts.PartWheel wheel : wheels) {
            if (drawer.isVisible(wheel)) {
                drawer.drawRotated(wheel, steering * wheel.rotDir);
            }
        }
    }

    private static void poseLightHatches(VehicleSpec info, float open, PartDrawer drawer) {
        for (VehicleParts.Hatch hatch : info.parts().lightHatches()) {
            if (drawer.isVisible(hatch)) {
                drawer.drawRotated(hatch, open * hatch.maxRot);
            }
        }
    }

    /** Road wheels: steered about {@code pos2}, then rolling about the X axis through {@code pos}. */
    private static void poseWheels(VehicleSpec info, float steer, float roll, PartDrawer drawer) {
        PartSink sink = drawer.sink();
        for (VehicleParts.PartWheel wheel : info.parts().wheels()) {
            if (!drawer.isVisible(wheel)) {
                continue;
            }
            sink.pushPose();
            sink.rotateAround(steer * wheel.rotDir, (float) wheel.rot.x, (float) wheel.rot.y, (float) wheel.rot.z,
                    wheel.pos2.x, wheel.pos2.y, wheel.pos2.z);
            sink.rotateAround(roll, 1.0F, 0.0F, 0.0F, wheel.pos.x, wheel.pos.y, wheel.pos.z);
            drawer.draw(wheel);
            sink.popPose();
        }
    }

    private static void poseThrottles(VehicleSpec info, float throttle, PartDrawer drawer) {
        if (!info.parts().havePartThrottle()) {
            return;
        }
        PartSink sink = drawer.sink();
        for (VehicleParts.Throttle lever : info.parts().throttles()) {
            if (!drawer.isVisible(lever)) {
                continue;
            }
            if (throttle == 0.0F) {
                drawer.draw(lever);
                continue;
            }
            sink.pushPose();
            drawer.rotateAbout(lever, throttle * lever.rot2);
            sink.translate(lever.slide.x * throttle, lever.slide.y * throttle, lever.slide.z * throttle);
            drawer.draw(lever);
            sink.popPose();
        }
    }

    /** Camera turrets follow the camera's yaw and/or pitch relative to the airframe. */
    private static void poseCameras(VehicleSpec info, float yaw, float pitch, PartDrawer drawer) {
        if (!info.parts().havePartCamera()) {
            return;
        }
        PartSink sink = drawer.sink();
        for (VehicleParts.Camera part : info.parts().cameras()) {
            if (!drawer.isVisible(part)) {
                continue;
            }
            if ((!part.yawSync || yaw == 0.0F) && (!part.pitchSync || pitch == 0.0F)) {
                drawer.draw(part);
                continue;
            }
            sink.pushPose();
            if (part.yawSync) {
                sink.rotateAround(yaw, 0.0F, -1.0F, 0.0F, part.pos.x, part.pos.y, part.pos.z);
            }
            if (part.pitchSync) {
                sink.rotateAround(pitch, 1.0F, 0.0F, 0.0F, part.pos.x, part.pos.y, part.pos.z);
            }
            drawer.draw(part);
            sink.popPose();
        }
    }

    /** Gear doors marked {@code hatch} use the door angle; {@code reverse} parts move opposite to the gear. */
    private static void poseLandingGear(VehicleSpec info, LandingGear gear, PartDrawer drawer) {
        if (gear == null) {
            return;
        }
        PartSink sink = drawer.sink();
        for (VehicleParts.LandingGear part : info.parts().landingGear()) {
            if (!drawer.isVisible(part)) {
                continue;
            }
            float angle = (part.reverse ? gear.reversed() : part.hatch ? gear.door() : gear.gear()) * part.maxRotFactor;
            float secondAngle = part.enableRot2 ? (part.reverse ? gear.reversed() : gear.gear()) * part.maxRotFactor2 : 0.0F;
            float fraction = part.slide == null ? 0.0F : part.reverse ? 1.0F - gear.slide() : gear.slide();
            if (angle == 0.0F && secondAngle == 0.0F && fraction == 0.0F) {
                // gear at rest: the common case for parked aircraft
                drawer.draw(part);
                continue;
            }
            sink.pushPose();
            drawer.rotateAbout(part, angle);
            if (secondAngle != 0.0F) {
                sink.rotateAround(secondAngle, (float) part.rot2.x, (float) part.rot2.y, (float) part.rot2.z,
                        part.pos.x, part.pos.y, part.pos.z);
            }
            if (fraction != 0.0F) {
                sink.translate(fraction * part.slide.x, fraction * part.slide.y, fraction * part.slide.z);
            }
            drawer.draw(part);
            sink.popPose();
        }
    }

    private static void poseWeaponBays(VehicleSpec info, List<Swing> bays, PartDrawer drawer) {
        for (int i = 0; i < bays.size(); i++) {
            VehicleParts.WeaponBay bay = info.parts().weaponBays().get(i);
            if (drawer.isVisible(bay)) {
                Swing door = bays.get(i);
                drawer.drawSlidingOrRotated(bay, bay.isSlide, door.slide(), door.angle() * bay.maxRotFactor);
            }
        }
    }
}
