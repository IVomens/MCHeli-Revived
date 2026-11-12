package com.ivomens.mcheli.vehicle.definition;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Mount;
import com.ivomens.mcheli.content.def.vehicle.Rack;
import com.ivomens.mcheli.content.def.vehicle.Seat;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;

import java.util.ArrayList;
import java.util.List;

/** Reads the seats and weapon mounts out of a vehicle file. */
final class CrewDefs {

    /** How far a rack's carried vehicle may look down and up; racks have no rider who looks. */
    private static final float RACK_MIN_PITCH = -30.0F;
    private static final float RACK_MAX_PITCH = 70.0F;

    private CrewDefs() {
    }

    /** Rider seats first (a UAV without seats gets one), then racks. */
    static List<SeatDef> seats(VehicleFile file) {
        List<SeatDef> seats = new ArrayList<>();
        for (Seat seat : VehicleRules.riderSeats(file)) {
            seats.add(new SeatDef(seat.position(), seat.unmountPosition(), seat.gunner(), seat.switchGunner(),
                    seat.fixedRotation(), seat.fixedYaw(), seat.fixedPitch(), seat.minPitch(), seat.maxPitch(),
                    seat.rotating(), seat.invertCamera(), camera(seat.camera()), null));
        }
        for (Rack rack : file.crew().racks()) {
            seats.add(new SeatDef(rack.position(), rack.unmountPosition(), false, false, false, rack.yaw(),
                    rack.pitch(), RACK_MIN_PITCH, RACK_MAX_PITCH, rack.rotating(), false, camera(rack.entry()),
                    new SeatDef.RackDef(rack.names(), rack.range())));
        }
        return seats;
    }

    private static SeatDef.SeatCameraDef camera(CameraSpec camera) {
        return camera == null ? null : new SeatDef.SeatCameraDef(VehicleRules.cameraPosition(camera),
                camera.fixedRotation(), camera.yaw(), camera.pitch());
    }

    static List<MountDef> mounts(VehicleFile file) {
        List<MountDef> mounts = new ArrayList<>();
        for (Mount mount : file.weapons()) {
            mounts.add(new MountDef(mount.weapon().name(), VehicleRules.mountPosition(mount), mount.yaw(),
                    mount.pitch(), mount.pilotCanUse(), mount.seat(), mount.defaultYaw(), mount.minYaw(),
                    mount.maxYaw(), mount.minPitch(), mount.maxPitch(), mount.turret()));
        }
        return mounts;
    }
}
