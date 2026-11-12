package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.guidance.LockOnSeeker;

import javax.annotation.Nullable;

/**
 * How a weapon kind fires. The client predicts a shot first ({@link #predict}): it applies recoil,
 * locks, charges, and decides what to ask the server for; the server then fires it ({@link #fire}).
 * A fire mode belongs to one {@link WeaponMount} and may keep that mount's state (a seeker, a charge,
 * a scheduled air strike).
 */
public interface FireMode {

    /**
     * Server: fires the shot.
     *
     * @return whether it was fired, which spends ammo and starts the cooldown
     */
    boolean fire(Shot shot);

    /**
     * Client: whether the shot happens. A kind that tells the server more than the trigger pull (a mode,
     * a lock) records it on the mount with {@link WeaponMount#setRequest}.
     */
    boolean predict(Shot shot);

    /** Once per tick of the mount's group, on each side; {@code cooldown} is the group's. */
    default void tick(WeaponMount mount, int cooldown) {
    }

    /** The lock-on seeker of the weapon, or null for weapons that do not lock. */
    @Nullable
    default LockOnSeeker seeker() {
        return null;
    }
}
