package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A vehicle's tank got fuel, or an electric vehicle charge: from a fuel can, bucket or other fluid container in
 * its fuel slot, a battery in that slot, another mod's pipe or energy cable, or a tanker vehicle nearby. Fired
 * after the fuel was added, once per transfer: a pipe filling the tank fires it for every batch it pushes, so a
 * listener that only cares about full refuelling should look at the vehicle's state instead of counting events.
 * Fuel that comes with the vehicle (placing it from an item that kept its fuel, a UAV station or a wingman
 * spawn filling it) and the server's infinite fuel setting are not refuelling.
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleRefueled extends McheliEvent {

    Vehicle vehicle();

    /** How much was added, in the tank's units (millibuckets, or energy for an electric vehicle); more than 0. */
    int amount();

    /** The tanker vehicle's entity that supplied it; null for the fuel slot, pipes and cables. */
    @Nullable
    Entity supplier();
}
