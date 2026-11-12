package com.ivomens.mcheli.combat.damage;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Damage that reached its victims through an area search, such as our explosions. It remembers the
 * area, so a vehicle can tell which of its parts the damage lands on. Everything else behaves like
 * an ordinary damage source; the source position is untouched.
 */
public final class AreaDamageSource extends DamageSource {

    private final AABB area;

    public AreaDamageSource(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity causing, AABB area) {
        super(type, direct, causing);
        this.area = area;
    }

    public AABB area() {
        return this.area;
    }
}
