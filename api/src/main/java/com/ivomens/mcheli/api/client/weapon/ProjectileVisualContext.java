package com.ivomens.mcheli.api.client.weapon;

import com.ivomens.mcheli.api.content.WeaponInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A client's copy of a projectile, as a {@link ProjectileVisual} sees it. MCHeli keeps one per projectile.
 *
 * <p><b>Side and thread.</b> Client only, on the client thread, during a call to a {@link ProjectileVisual}.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface ProjectileVisualContext {

    /** The projectile entity. */
    Entity entity();

    /** The client level it is in (a {@code ClientLevel}; the API names no client class). */
    Level level();

    /** The weapon type that fired it. */
    WeaponInfo weapon();

    /** Where it is now. */
    Vec3 position();

    /** Its motion, in blocks per tick. */
    Vec3 velocity();

    /** Ticks it has flown, as far as this client knows. */
    int ticksFlown();

    /** The entity it is locked on, when the server told clients; null otherwise. */
    @Nullable
    Entity target();

    /** Free storage for this projectile's visuals, created empty for each projectile on this client. */
    CompoundTag data();
}
