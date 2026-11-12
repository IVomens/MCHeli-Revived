package com.ivomens.mcheli.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * One of MCHeli's projectiles (a bullet, rocket, missile, bomb or shell) hit a block or an entity, before
 * its damage and explosion are applied. A projectile that pierces fires this once per hit. Fuzes that set a
 * projectile off in the air (time, proximity, airburst) are not impacts.
 *
 * <p>Server side, on the server thread. Stable since API 1.0.
 */
public interface ProjectileImpact extends McheliEvent {

    /** The projectile entity. */
    Entity projectile();

    /** The id of the weapon type that fired it, as {@link com.ivomens.mcheli.api.content.WeaponInfo#id()}. */
    String weaponId();

    /** Who fired it, when that is known. */
    @Nullable
    Entity shooter();

    /** What it hit: a {@code BlockHitResult} or an {@code EntityHitResult}. */
    HitResult hit();
}
