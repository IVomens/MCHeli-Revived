package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.api.vehicle.VehicleKind;

/**
 * What may carry a vehicle, and what a vehicle may carry. Server side.
 *
 * @param minecartHelicopters helicopters hop onto minecarts
 * @param minecartPlanes      planes and ships hop onto minecarts
 * @param minecartTanks       tanks hop onto minecarts
 * @param minecartVehicles    ground vehicles hop onto minecarts (never while held at their placed point)
 * @param holdAtPlacedPoint   ground vehicles stay where they were placed ({@code FixVehicleAtPlacedPoint})
 * @param racksInFlight       vehicles may be loaded onto a rack while flying
 */
public record CarriageRules(boolean minecartHelicopters, boolean minecartPlanes, boolean minecartTanks,
                            boolean minecartVehicles, boolean holdAtPlacedPoint, boolean racksInFlight) {

    /** Whether a vehicle of the kind rides minecarts; emplaced vehicles held in place never do. */
    public boolean ridesMinecarts(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> this.minecartHelicopters;
            case PLANE, SHIP -> this.minecartPlanes;
            case TANK -> this.minecartTanks;
            case VEHICLE -> this.minecartVehicles && !this.holdAtPlacedPoint;
        };
    }
}
