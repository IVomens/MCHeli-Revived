package com.ivomens.mcheli.api.vehicle;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * An extra hit box of a large vehicle (a wing, a hull section): an entity that takes hits for the vehicle
 * it belongs to. Projectiles pass through it; beams that reach it hit unless they come from its own vehicle.
 *
 * <p>Both sides, on the thread of the hit box's level. Stable since API 1.0; implemented by MCHeli only, so a
 * minor version may add methods.
 */
public interface VehicleHitBox {

    /** The vehicle the hit box belongs to, or null once it lost it. */
    @Nullable
    Entity owner();
}
