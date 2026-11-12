package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A lock-on seeker completed its lock: the weapon can fire at the target now. Fired once each time the lock
 * becomes complete, on the same side and thread as {@link LockOnStarted}.
 *
 * <p>Stable since API 1.0.
 */
public interface LockOnCompleted extends McheliEvent {

    /** Who locked: the player or mob aiming the weapon. */
    Entity user();

    /** What is locked. */
    Entity target();

    /** The vehicle the user is aboard, if any. */
    @Nullable
    Vehicle vehicle();
}
