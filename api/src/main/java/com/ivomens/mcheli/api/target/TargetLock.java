package com.ivomens.mcheli.api.target;

import com.ivomens.mcheli.api.event.SeekerType;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A lock on an addon's target, handed to {@link TargetProfile#onLockedOn}. Only valid while that call runs.
 *
 * <p>On the thread the callback runs on. Since API 1.1; implemented by MCHeli only, so a minor version may add
 * methods.
 */
public interface TargetLock {

    /** What is locked: the entity the profile was registered for. */
    Entity target();

    /** Who locked: the player or AI gunner aiming the weapon, or the pilot whose radar locked. */
    Entity user();

    /** The vehicle the user is aboard, or null when there is none (a handheld launcher). */
    @Nullable
    Vehicle vehicle();

    /** What the seeker locks with, which says whether flares or chaff will help. */
    SeekerType seeker();
}
