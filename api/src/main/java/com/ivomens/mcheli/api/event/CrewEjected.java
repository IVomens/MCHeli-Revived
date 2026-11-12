package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

/**
 * A crew member left a vehicle by ejection seat: the pilot or the copilot pulled the handle, or the vehicle
 * threw them out as it was destroyed. Each ejection costs a parachute from the vehicle's cargo. When the pilot
 * ejects and takes a copilot that is not a player along, each fires its own event. Fired once the rider has
 * left the seat ({@link SeatLeft} came first) and hangs under their parachute. Leaving a seat any other way
 * (dismounting, bailing out with a parachute) does not fire it.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface CrewEjected extends McheliEvent {

    Vehicle vehicle();

    /** Who was ejected: a player, a mob or an AI gunner. */
    Entity rider();

    /** The seat they left: 0 for the pilot, 1 for the copilot. */
    int seatId();
}
