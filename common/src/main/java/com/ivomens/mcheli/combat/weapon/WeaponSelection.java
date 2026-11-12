package com.ivomens.mcheli.combat.weapon;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Which weapon each crew member of a vehicle has selected. */
public interface WeaponSelection {

    /** The weapon the crew member has selected, or null when the seat has none. */
    @Nullable
    WeaponGroup current(@Nullable Entity user);
}
