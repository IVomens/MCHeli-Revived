package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

/**
 * A vehicle left a server level, for good or not: a player broke it, its wreck burnt out, a command killed
 * it, its chunk unloaded or it changed dimension. {@link #reason()} says which; only
 * {@link Entity.RemovalReason#shouldDestroy()} reasons remove it for good. Fired as the entity leaves, after
 * it is marked removed.
 *
 * <p>Server side, on the server thread. Stable since API 1.0.
 */
public interface VehicleRemoved extends McheliEvent {

    Vehicle vehicle();

    /** Why it left: {@code KILLED} and {@code DISCARDED} for good, {@code UNLOADED_TO_CHUNK} and the others not. */
    Entity.RemovalReason reason();
}
