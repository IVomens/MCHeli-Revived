package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player is about to open one of a vehicle's screens: the pilot's panel with its key, or the cargo with a
 * sneak-click. Fired after MCHeli's own checks (the owner's lock, the vehicle alive and in the player's
 * level, the pilot's seat for the panel) and before the menu opens. Cancelling it opens nothing.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleScreenOpenAttempt extends McheliEvent, Cancellable {

    ServerPlayer player();

    Vehicle vehicle();

    VehicleScreen screen();
}
