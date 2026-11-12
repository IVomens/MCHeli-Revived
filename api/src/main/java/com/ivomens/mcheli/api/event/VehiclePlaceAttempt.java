package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A player is about to place a vehicle from its item: on a block, on an empty minecart, or by launching it
 * from a UAV station. Fired after MCHeli's own checks (room to place it, creative-only types, the server's
 * vehicle limits) and before the vehicle enters the level. Cancelling it places nothing and keeps the item;
 * MCHeli then sends the player's inventory to their client again, which had already used the item.
 * {@link VehicleSpawned} follows a placement that went ahead.
 *
 * <p>Dispensers, commands and other mods spawning vehicles are not asked.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehiclePlaceAttempt extends McheliEvent, Cancellable {

    ServerPlayer player();

    /**
     * The vehicle that would be placed, at the position and facing it would have. It is not in the level yet:
     * it has no crew and the state it takes from the item (fuel, ammunition) is not loaded yet, so read only
     * its type and position.
     */
    Vehicle vehicle();

    /** The item the vehicle comes from; do not change it. */
    ItemStack item();
}
