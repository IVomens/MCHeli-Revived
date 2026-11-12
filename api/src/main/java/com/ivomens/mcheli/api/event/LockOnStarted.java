package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A lock-on seeker (of a guided missile, or a handheld launcher) picked a target and started building its
 * lock. {@link LockOnCompleted} follows if the target stays in the seeker's cone long enough.
 *
 * <p><b>Side.</b> On the side that runs the seeker: the client of the player who locks (the server only
 * learns the target when the missile fires), or the server for AI gunners and wingmen. Check
 * {@code user().level().isClientSide()}. On that side's main thread. Stable since API 1.0.
 */
public interface LockOnStarted extends McheliEvent {

    /** Who is locking: the player or mob aiming the weapon. */
    Entity user();

    /** What is being locked. */
    Entity target();

    /** The vehicle the user is aboard, if any. */
    @Nullable
    Vehicle vehicle();
}
