package com.ivomens.mcheli.api.weapon;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What a {@link ProjectileEffect} works with: the projectile, plus what MCHeli can do for it that the vanilla game
 * cannot (its own projectiles and explosions, a vehicle's engine). To end the projectile without exploding, discard
 * its {@link #entity()}.
 *
 * <p><b>Side and thread.</b> Server side, on the server thread, only during a call to a {@link ProjectileEffect}.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface ProjectileEffectContext extends ProjectileView {

    /**
     * Launches a projectile of another MCHeli weapon (the projectile its {@code Type} fires) at the position with
     * the velocity, as a sub-munition of this one: credited to the same shooter, with that weapon's damage and
     * explosion, and {@link ProjectileView#isSubmunition()} true. Its own effects run as well, so an effect that
     * spawns projectiles of its own weapon should skip sub-munitions.
     *
     * @param weaponId the weapon's id, as {@link com.ivomens.mcheli.api.content.WeaponInfo#id()}
     * @param velocity the motion in blocks per tick; its length is the speed (MCHeli caps it at 3.9)
     * @return the spawned projectile entity
     * @throws IllegalArgumentException when no loaded weapon has that id or its type fires no projectile
     *                                  (a laser, a gun pod's buckshot, close air support...)
     */
    Entity spawnProjectile(String weaponId, Vec3 position, Vec3 velocity);

    /**
     * An explosion at the point with this weapon's explosion rules (radius, block damage, fire, damage factors,
     * through-wall settings), credited to the projectile's shooter.
     *
     * @param power the explosion power, as {@code Explosion: Power} in a weapon file
     */
    void explode(Vec3 at, float power);

    /**
     * Stops a vehicle's engine for a while, as an electromagnetic pulse would: it loses power and spools down as
     * if out of fuel, then can be started again. Stalling a stalled engine keeps the later end. Every client sees
     * it. Does nothing to a destroyed vehicle.
     *
     * @param ticks how long, at least 1
     */
    void stallEngine(Vehicle vehicle, int ticks);
}
