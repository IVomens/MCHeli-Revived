package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelCache;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.GroundParts;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.HullPose;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.PlaneParts;

import java.util.List;

/**
 * Lays out a vehicle's model for a {@link VehicleRenderSnapshot}: every part the definition has, in
 * drawing order, with its transform relative to the airframe. Both backends draw through it, so a
 * part ends up in the same place whichever one draws it.
 */
public final class VehicleModelPoser {

    private VehicleModelPoser() {
    }

    /**
     * Moves from the entity's origin to the body origin (with the global vehicle Y offset) and turns
     * like the airframe.
     */
    public static void applyHull(PartSink sink, HullPose hull) {
        sink.translate(0.0, McheliEntity.GLOBAL_Y_OFFSET, 0.0);
        sink.rotate(hull.yaw(), 0.0F, -1.0F, 0.0F);
        sink.rotate(hull.pitch(), 1.0F, 0.0F, 0.0F);
        if (hull.rolls()) {
            sink.rotate(hull.roll(), 0.0F, 0.0F, 1.0F);
        }
    }

    /** Sends every part of the model to the sink, which must already be in the airframe's pose. */
    public static void pose(VehicleRenderSnapshot snapshot, PartSink sink) {
        PartDrawer drawer = new PartDrawer(snapshot.models(), snapshot.detail().pixelsPerBlock(), sink);
        VehicleSpec info = snapshot.info();
        VehicleKind kind = info != null ? info.kind() : null;
        if (kind == VehicleKind.PLANE || kind == VehicleKind.SHIP) {
            posePlaneParts(info, snapshot.parts().plane(), drawer);
            poseAirframe(snapshot, drawer);
        } else {
            poseAirframe(snapshot, drawer);
            if (kind == VehicleKind.HELICOPTER) {
                poseRotors(info, snapshot.parts().rotors(), drawer);
            } else if (kind == VehicleKind.VEHICLE && snapshot.parts().ground() != null) {
                new GroundPartTree(info, snapshot.hull(), snapshot.parts().ground(), drawer).pose();
            }
        }
        CommonPartPoser.pose(snapshot, drawer);
    }

    /** The airframe model and, when parked or far away, the static crawler tracks. */
    private static void poseAirframe(VehicleRenderSnapshot snapshot, PartDrawer drawer) {
        drawer.drawBody();
        BakedModelBase body = drawer.bakedBody();
        if (body != null && snapshot.parts().staticTracks()) {
            PartMesh tracks = StaticTracks.of(body, snapshot.info());
            if (tracks != null) {
                drawer.sink().draw(tracks);
            }
        }
    }

    /**
     * Each helicopter blade turns about its rotor's axis. With {@code oldRenderMethod} the axis passes
     * through the model origin after moving to the rotor position (blade models modelled at the
     * origin); otherwise it passes through the rotor position (blades modelled in place).
     */
    private static void poseRotors(VehicleSpec info, List<Angles> rotors, PartDrawer drawer) {
        PartSink sink = drawer.sink();
        for (int i = 0; i < rotors.size(); i++) {
            VehicleParts.HeliRotor rotor = info.kindParts().heliRotors().get(i);
            if (!drawer.isVisible(rotor)) {
                continue;
            }
            Angles blades = rotors.get(i);
            sink.pushPose();
            if (rotor.oldRenderMethod) {
                sink.translate(rotor.pos.x, rotor.pos.y, rotor.pos.z);
            }
            for (int b = 0; b < blades.size(); b++) {
                sink.pushPose();
                if (rotor.oldRenderMethod) {
                    sink.rotate(blades.get(b), (float) rotor.rot.x, (float) rotor.rot.y, (float) rotor.rot.z);
                } else {
                    sink.rotateAround(blades.get(b), (float) rotor.rot.x, (float) rotor.rot.y, (float) rotor.rot.z,
                            rotor.pos.x, rotor.pos.y, rotor.pos.z);
                }
                drawer.draw(rotor);
                sink.popPose();
            }
            sink.popPose();
        }
    }

    /** Nozzles, folding wings with their pylons, and tilt rotors with their blades. */
    private static void posePlaneParts(VehicleSpec info, PlaneParts parts, PartDrawer drawer) {
        if (parts.nozzles()) {
            for (VehicleParts.DrawnPart nozzle : info.kindParts().nozzles()) {
                if (drawer.isVisible(nozzle)) {
                    drawer.drawRotated(nozzle, parts.nozzleAngle());
                }
            }
        }
        if (parts.wings()) {
            poseWings(info, parts.wingSweep(), drawer);
        }
        if (parts.rotors()) {
            poseTiltRotors(info, parts.nozzleAngle(), parts.propellerSpin(), drawer);
        }
    }

    private static void poseWings(VehicleSpec info, float sweep, PartDrawer drawer) {
        PartSink sink = drawer.sink();
        for (VehicleParts.Wing wing : info.kindParts().wings()) {
            sink.pushPose();
            sink.rotateAround(sweep * wing.maxRotFactor, (float) wing.rot.x, (float) wing.rot.y, (float) wing.rot.z,
                    wing.pos.x, wing.pos.y, wing.pos.z);
            drawer.drawIfVisible(wing);
            if (wing.pylonList != null) {
                for (VehicleParts.Pylon pylon : wing.pylonList) {
                    if (drawer.isVisible(pylon)) {
                        drawer.drawRotated(pylon, sweep * pylon.maxRotFactor);
                    }
                }
            }
            sink.popPose();
        }
    }

    /** Tilting rotors (tilt follows the nozzle angle), each with its spinning blades. */
    private static void poseTiltRotors(VehicleSpec info, float tilt, float spin, PartDrawer drawer) {
        PartSink sink = drawer.sink();
        for (VehicleParts.Propeller rotor : info.kindParts().propellers()) {
            sink.pushPose();
            sink.rotateAround(tilt * rotor.maxRotFactor, (float) rotor.rot.x, (float) rotor.rot.y,
                    (float) rotor.rot.z, rotor.pos.x, rotor.pos.y, rotor.pos.z);
            drawer.drawIfVisible(rotor);
            for (VehicleParts.Blade blade : rotor.blades) {
                drawer.drawBladeRing(blade, blade.numBlade, blade.rotBlade, spin);
            }
            sink.popPose();
        }
    }

    /**
     * The aimed part tree of a ground vehicle (mounted guns, launchers): each part turns with the
     * gunner's aim, spins its barrel or recoils, and carries its children along.
     */
    private static final class GroundPartTree {

        private final HullPose hull;
        private final GroundParts parts;
        private final PartDrawer drawer;
        private final List<VehicleParts.VPart> roots;
        private int node;

        GroundPartTree(VehicleSpec info, HullPose hull, GroundParts parts, PartDrawer drawer) {
            this.hull = hull;
            this.parts = parts;
            this.drawer = drawer;
            this.roots = info.kindParts().turretParts();
        }

        void pose() {
            for (VehicleParts.VPart part : this.roots) {
                this.pose(part);
            }
        }

        private void pose(VehicleParts.VPart part) {
            PartSink sink = this.drawer.sink();
            int index = this.node++;
            sink.pushPose();
            if (part.rotPitch || part.rotYaw || part.type == GroundPartCapture.PART_BARREL) {
                sink.translate(part.pos.x, part.pos.y, part.pos.z);
                if (part.rotYaw) {
                    sink.rotate(-this.parts.aimYaw() + this.hull.yaw(), 0.0F, 1.0F, 0.0F);
                }
                if (part.rotPitch) {
                    sink.rotate(this.parts.aimPitch() - this.hull.pitch(), 1.0F, 0.0F, 0.0F);
                }
                if (part.type == GroundPartCapture.PART_BARREL) {
                    sink.rotate(this.parts.barrel(), 0.0F, 0.0F, -1.0F);
                }
                sink.translate(-part.pos.x, -part.pos.y, -part.pos.z);
            }
            if (part.type == GroundPartCapture.PART_RECOILING) {
                sink.translate(0.0, 0.0, -part.recoilBuf * this.parts.recoil().get(index));
            }
            if (part.child != null) {
                for (VehicleParts.VPart child : part.child) {
                    this.pose(child);
                }
            }
            boolean hidden = !part.drawFP && this.parts.hideFirstPerson();
            if (!hidden && this.parts.loaded().get(index)) {
                this.drawer.drawIfVisible(part);
                // a part can also be a model of its own in the shared vehicles folder
                IModelCustom shared = ModelCache.find("vehicles", part.modelName);
                if (shared != null) {
                    sink.draw(shared);
                }
            }
            sink.popPose();
        }
    }
}
