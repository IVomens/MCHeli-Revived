package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player is about to lock or unlock a vehicle: the owner's sneak-click with an empty hand, or
 * {@code /mcheli lock} and {@code /mcheli unlock}; the command also makes the player the owner of a vehicle
 * that has none. Fired after MCHeli's own checks (locking switched on, the player the owner or an operator)
 * and before anything changes. Cancelling it changes neither the lock nor the owner; the command then fails
 * with a message, the sneak-click does nothing.
 *
 * <p>Placing a vehicle makes the player its owner without this event; {@link VehiclePlaceAttempt} covers that.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleLockAttempt extends McheliEvent, Cancellable {

    ServerPlayer player();

    Vehicle vehicle();

    /** True to lock the vehicle, false to unlock it. */
    boolean locking();

    /** Whether the vehicle has no owner yet and the player would become it. */
    boolean claimsOwnership();
}
