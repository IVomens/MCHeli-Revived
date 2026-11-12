package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player is about to take a seat of a vehicle: by clicking the vehicle (the pilot's seat, or the first free
 * seat behind a pilot), by clicking a passenger seat, or by moving to the next or previous seat with the seat
 * keys. Fired after MCHeli's own checks (the owner's lock, a free seat, a closed canopy opening first) and
 * before the player is seated. Cancelling it keeps the player where they are, without a message: tell them why
 * yourself. {@link SeatBoarded} follows a boarding that went ahead.
 *
 * <p>Only players are asked. Mobs seated as crew, AI gunners, commands, other mods calling
 * {@code startRiding} and riders that load together with their vehicle are not.
 *
 * <p>The player's client predicts a pilot's boarding, and some vehicles turn the player's view to where the
 * last driver looked. After a cancellation MCHeli sends the client the view rotation the server has, so
 * nothing of the prediction stays.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleBoardAttempt extends McheliEvent, Cancellable {

    ServerPlayer player();

    Vehicle vehicle();

    /** The seat the player would take: 0 for the pilot, 1 and up for the passenger seats. */
    int seatId();
}
