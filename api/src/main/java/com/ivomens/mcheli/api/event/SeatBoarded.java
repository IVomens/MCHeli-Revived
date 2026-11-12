package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

/**
 * An entity (a player, a mob, an AI gunner) took a seat of a vehicle: the pilot's seat, which is the vehicle
 * itself, or a passenger seat. Also fired when the two load together and the rider mounts again. Moving to
 * another seat is a {@link SeatLeft} followed by a {@code SeatBoarded}.
 *
 * <p>Server side, on the server thread, after the rider is aboard. Stable since API 1.0.
 */
public interface SeatBoarded extends McheliEvent {

    Vehicle vehicle();

    Entity rider();

    /** The seat taken: 0 for the pilot, 1 and up for the passenger seats (see {@link Vehicle#seats()}). */
    int seatId();
}
