package com.ivomens.mcheli.api.weapon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * What a {@link GuidanceLaw} works with: the projectile and its target, plus the seeker and airframe limits of
 * the weapon as its file sets them ({@code Missile: TurningFactor, MaxDegreeOfMissile, PDHDNMaxDegree, Heat,
 * Radar}). MCHeli applies the same limits to the law's commands as to its own guidance; the queries here let a
 * law look before it commands.
 *
 * <p><b>Side and thread.</b> Server side, on the server thread, only during {@link GuidanceLaw#steer} or
 * {@link GuidanceLaw#decoyed}.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface GuidanceContext extends ProjectileView {

    /**
     * How fast the projectile may turn: the weapon's {@code TurningFactor}, the share of the way to the commanded
     * direction it turns per tick, from 0 (not at all) to 1 (at once).
     */
    double turnRate();

    /**
     * The half angle of the seeker's field of view in degrees (the weapon's {@code MaxDegreeOfMissile}). A
     * {@link SteeringCommand#track tracked} point outside it breaks the lock.
     */
    float seekerConeDegrees();

    /**
     * Whether the point is inside the seeker's field of view around the flight direction. During the climb of a
     * top attack the seeker sees everywhere, as MCHeli's own missiles do.
     */
    boolean isInSeekerCone(Vec3 point);

    /**
     * Whether the target is hidden by countermeasures this weapon falls for: flares near the target for a heat
     * seeker, chaff for a radar missile (the weapon's {@code Heat} and {@code Radar} settings), checked every third
     * tick as for MCHeli's missiles. False without a target. When it is true MCHeli asks
     * {@link GuidanceLaw#decoyed} instead of {@link GuidanceLaw#steer}.
     */
    boolean isTargetDecoyed();

    /**
     * Locks the projectile on an entity, or drops its lock with null. Missile warnings of the target's vehicle see a
     * locked projectile, as they see MCHeli's own missiles.
     */
    void setTarget(@Nullable Entity target);
}
