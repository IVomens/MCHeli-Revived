package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

/**
 * An entity left a seat of a vehicle: it dismounted, switched seats, was ejected, or it or the vehicle left
 * the level. Fired as the rider leaves, while {@link Vehicle#seats()} still lists it in the seat.
 *
 * <p>Server side, on the server thread. Stable since API 1.0.
 */
public interface SeatLeft extends McheliEvent {

    Vehicle vehicle();

    Entity rider();

    /** The seat left: 0 for the pilot, 1 and up for the passenger seats. */
    int seatId();
}
