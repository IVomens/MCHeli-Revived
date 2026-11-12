package com.ivomens.mcheli.api.weapon;

import com.ivomens.mcheli.api.content.WeaponInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One of MCHeli's projectiles in flight, as a guidance law or a projectile effect sees it. MCHeli keeps one view
 * per projectile and hands the same one to every call, so the values are always the current ones.
 *
 * <p><b>Side and thread.</b> Server side, on the server thread, and only during a call to a {@link GuidanceLaw} or
 * {@link ProjectileEffect}; do not keep it.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface ProjectileView {

    /** The projectile entity. */
    Entity entity();

    /** The level it flies in. */
    ServerLevel level();

    /** The weapon type that fired it. */
    WeaponInfo weapon();

    /** Where it is now. */
    Vec3 position();

    /** Its motion, in blocks per tick (a fast round moves several such steps per tick). */
    Vec3 velocity();

    /** Its speed along the flight direction, blocks per step. */
    double speed();

    /** Ticks it has flown, counted from its first tick. */
    int ticksFlown();

    /** Whether it is a bomblet or rocket a cluster weapon's projectile released, rather than one a weapon fired. */
    boolean isSubmunition();

    /** Who fired it and is credited with its hits; null when nobody is known (a dispenser, a lost owner). */
    @Nullable
    Entity shooter();

    /** What it was fired from: the vehicle, or the shooter itself for a handheld weapon; null when unknown. */
    @Nullable
    Entity launcher();

    /** The entity it is locked on, or null when it has no target. */
    @Nullable
    Entity target();

    /** The point it was sent to (a laser spot, a GPS or point-attack target), or null when it has none. */
    @Nullable
    Vec3 targetPoint();

    /**
     * Free storage for this projectile's behaviours, shared by its guidance law and its effects (use keys of your
     * own namespace). Created empty for each projectile; never saved or sent to clients.
     */
    CompoundTag data();
}
