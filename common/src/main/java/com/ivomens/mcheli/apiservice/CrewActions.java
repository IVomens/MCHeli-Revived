package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.action.ActionResult;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Seating and unseating crew for the API, the way the game itself does it. */
final class CrewActions {

    private CrewActions() {
    }

    /** Seats the rider (0 is the pilot), taking it off whatever it rides first. */
    static ActionResult board(AircraftEntity vehicle, Entity rider, int seatId) {
        SeatsComponent seats = vehicle.seats();
        VehicleSpec spec = vehicle.spec();
        if (vehicle.isRemoved()) {
            return ActionResult.VEHICLE_REMOVED;
        }
        if (vehicle.isDestroyed()) {
            return ActionResult.VEHICLE_DESTROYED;
        }
        if (spec == null || !seats.isValidSeatId(seatId)) {
            return ActionResult.INVALID_SEAT;
        }
        Entity current = seats.riderOf(seatId);
        if (current == rider) {
            return ActionResult.NO_CHANGE;
        }
        if (current != null) {
            return ActionResult.SEAT_OCCUPIED;
        }
        if (!seats.canTake(seatId)) {
            return ActionResult.SEAT_BLOCKED;
        }
        Entity mount = seatId == 0 ? vehicle : seats.seat(seatId - 1);
        if (mount == null || rider == vehicle || rider.level() != vehicle.level()) {
            return ActionResult.REFUSED;
        }
        if (rider.isPassenger()) {
            rider.stopRiding();
        }
        if (seatId == 0 && rider instanceof Player player) {
            vehicle.pilotBoarding().board(player, spec);
        } else {
            rider.startRiding(mount);
        }
        return seats.riderOf(seatId) == rider ? ActionResult.SUCCESS : ActionResult.REFUSED;
    }

    /** Puts the rider out at its seat's exit, as if it got off. */
    static ActionResult dismount(AircraftEntity vehicle, Entity rider) {
        if (vehicle.isRemoved()) {
            return ActionResult.VEHICLE_REMOVED;
        }
        if (vehicle.seats().seatIdOf(rider) < 0) {
            return ActionResult.NOT_ABOARD;
        }
        rider.stopRiding();
        return ActionResult.SUCCESS;
    }

    static ActionResult eject(AircraftEntity vehicle, int seatId) {
        if (vehicle.isRemoved()) {
            return ActionResult.VEHICLE_REMOVED;
        }
        if (!vehicle.seats().isValidSeatId(seatId)) {
            return ActionResult.INVALID_SEAT;
        }
        Entity rider = vehicle.seats().riderOf(seatId);
        return rider == null ? ActionResult.SEAT_EMPTY : dismount(vehicle, rider);
    }

    static int ejectAll(AircraftEntity vehicle) {
        int count = 0;
        for (int seatId = vehicle.seats().count(); seatId >= 0; seatId--) {
            if (eject(vehicle, seatId) == ActionResult.SUCCESS) {
                count++;
            }
        }
        return count;
    }
}
