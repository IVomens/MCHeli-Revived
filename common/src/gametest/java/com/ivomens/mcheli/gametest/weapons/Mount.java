package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** What fires a weapon in a snapshot: a vehicle that carries it, or a soldier holding it. */
interface Mount {

    /** Advances boarding and weapon selection by one tick; true once the weapon can fire. */
    boolean prepare();

    /**
     * Pulls the trigger the way the game's own path does.
     *
     * @param target the locked target for guided weapons, or null
     */
    void fire(@Nullable Entity target);

    /** Per-tick upkeep the game does for this mount after the shot (cooldowns of a carried set). */
    void tick();

    /** The weapon set whose ammo, heat and cooldown are recorded. */
    WeaponGroup weapons();

    /** One line for the golden header. */
    String describe();

    /** Removes the vehicle and players this mount created. */
    void remove();
}
