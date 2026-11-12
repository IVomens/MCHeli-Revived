package com.ivomens.mcheli.vehicle.definition;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A seat, or a rack that carries another vehicle. Racks come after the riders' seats, in the same
 * order the entity numbers its seat entities.
 *
 * @param pos             where the rider sits, relative to the vehicle
 * @param unmountPos      where the rider is put when leaving, or null for the default
 * @param gunner          whether the seat starts in gunner view
 * @param switchGunner    whether the rider can switch to gunner view
 * @param fixRot          whether the rider's view is locked to {@code fixYaw}/{@code fixPitch}
 * @param minPitch        lowest the rider can look
 * @param maxPitch        highest the rider can look
 * @param rotSeat         whether the seat turns with the rider's view
 * @param invertCameraPos whether the third-person camera sits on the other side
 * @param camera          the seat's own camera position, or null for the vehicle's
 * @param rack            what the rack carries; null for a rider's seat
 */
public record SeatDef(Vec3 pos, @Nullable Vec3 unmountPos, boolean gunner, boolean switchGunner, boolean fixRot,
                      float fixYaw, float fixPitch, float minPitch, float maxPitch, boolean rotSeat,
                      boolean invertCameraPos, @Nullable SeatCameraDef camera, @Nullable RackDef rack) {

    public boolean isRack() {
        return this.rack != null;
    }

    /** A seat camera; the position already includes the global vehicle Y offset. */
    public record SeatCameraDef(Vec3 pos, boolean fixRot, float yaw, float pitch) {
    }

    /**
     * @param vehicleNames the vehicles that fit the rack
     * @param range        how close a vehicle must be to its entry position (the seat camera) to load
     */
    public record RackDef(List<String> vehicleNames, float range) {

        public RackDef {
            vehicleNames = List.copyOf(vehicleNames);
        }
    }
}
