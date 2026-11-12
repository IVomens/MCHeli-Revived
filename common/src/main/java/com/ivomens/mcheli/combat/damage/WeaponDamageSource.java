package com.ivomens.mcheli.combat.damage;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Damage of a weapon whose hit has no projectile to tell the weapon by, such as a beam: it remembers the
 * weapon's type, for the API's damage events. Everything else behaves like an ordinary damage source.
 */
public final class WeaponDamageSource extends DamageSource {

    private final String weaponId;

    public WeaponDamageSource(Holder<DamageType> type, Entity direct, @Nullable Entity causing, String weaponId) {
        super(type, direct, causing);
        this.weaponId = weaponId;
    }

    public String weaponId() {
        return this.weaponId;
    }
}
