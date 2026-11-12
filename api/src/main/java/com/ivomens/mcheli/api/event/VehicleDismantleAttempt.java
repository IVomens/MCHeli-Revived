package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player is about to break a vehicle down into its item (in creative mode: remove it) by hitting it. Fired
 * after MCHeli's own checks (the owner's lock, the server's durability rules, a pickaxe where one is needed,
 * nobody aboard), and after the {@link VehicleDamaged} event of the same hit, which can stop it first.
 * Cancelling it leaves the vehicle in place, and the hit then counts as an ordinary hit, as for a player a
 * locked vehicle refuses; cancel the hit's {@link VehicleDamaged} as well to make it harmless.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleDismantleAttempt extends McheliEvent, Cancellable {

    ServerPlayer player();

    Vehicle vehicle();
}
