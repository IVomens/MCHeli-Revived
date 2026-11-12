package com.ivomens.mcheli.combat.weapon;

import net.minecraft.world.entity.Entity;

import java.util.Map;

/**
 * Damage multipliers by the exact class of the entity hit; an entity of any other class takes the damage as
 * it is.
 */
public final class DamageFactor {

    private final Map<Class<? extends Entity>, Float> byClass;

    public DamageFactor(Map<Class<? extends Entity>, Float> byClass) {
        this.byClass = Map.copyOf(byClass);
    }

    /** The multipliers by entity class. */
    public Map<Class<? extends Entity>, Float> factors() {
        return this.byClass;
    }

    public float getDamageFactor(Class<? extends Entity> type) {
        return this.byClass.getOrDefault(type, 1.0F);
    }

    public float getDamageFactor(Entity entity) {
        return entity != null ? this.getDamageFactor(entity.getClass()) : 1.0F;
    }
}
