package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.VehicleParts.CameraPosition;
import com.ivomens.mcheli.aircraft.VehicleParts.RepellingHook;
import com.ivomens.mcheli.aircraft.VehicleParts.RideRack;

import java.util.List;

/**
 * Where a vehicle's crew sits and how it gets in and out.
 *
 * @param seats           rider seats first, then racks
 * @param riderSeats      how many of {@code seats} are rider seats
 * @param racks           how many of {@code seats} are racks
 * @param exclusions      groups of seats (indexes into {@code seats}) of which only one may be taken at a time
 * @param cameraPositions the third person camera positions; without any in the file, one at the model's origin
 * @param rideRacks       racks of other vehicles this one can ride on
 * @param repellingHooks  where crew rope down; none when the vehicle drops its crew by parachute
 * @param mobDrop         where crew jump out during a parachute drop
 */
public record CrewLayout(List<SeatInfo> seats, int riderSeats, int racks, List<Integer[]> exclusions,
                         List<CameraPosition> cameraPositions, List<RideRack> rideRacks,
                         List<RepellingHook> repellingHooks, MobDropOption mobDrop) {

    public CrewLayout {
        seats = List.copyOf(seats);
        exclusions = List.copyOf(exclusions);
        cameraPositions = List.copyOf(cameraPositions);
        rideRacks = List.copyOf(rideRacks);
        repellingHooks = List.copyOf(repellingHooks);
    }

    /** Seats and racks together. */
    public int seatsAndRacks() {
        return this.seats.size();
    }

    public boolean haveRepellingHook() {
        return !this.repellingHooks.isEmpty();
    }
}
