package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.content.def.weapon.DamageFactors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a weapon file's damage factors to the entity classes they apply to: {@code Player}, the named
 * vehicle targets ({@code Helicopter}, ...) the vehicle core hands in, or any entity class by its name.
 */
final class WeaponDamageTargets {

    private WeaponDamageTargets() {
    }

    /** The factors of the targets that resolve; null when none does, as run-time code expects. */
    @Nullable
    static DamageFactor resolve(Map<String, Float> factors, Map<String, Class<? extends Entity>> vehicleTargets) {
        Map<Class<? extends Entity>, Float> byClass = new LinkedHashMap<>();
        for (Map.Entry<String, Float> factor : factors.entrySet()) {
            Class<? extends Entity> target = targetClass(factor.getKey(), vehicleTargets);
            if (target != null) {
                byClass.put(target, factor.getValue());
            }
        }
        return byClass.isEmpty() ? null : new DamageFactor(byClass);
    }

    @Nullable
    private static Class<? extends Entity> targetClass(String target,
                                                       Map<String, Class<? extends Entity>> vehicleTargets) {
        if (target.equals(DamageFactors.PLAYER)) {
            return Player.class;
        }
        Class<? extends Entity> vehicle = vehicleTargets.get(target);
        return vehicle != null ? vehicle : entityClass(target);
    }

    /** A YAML damage factor may name any entity class. */
    @Nullable
    private static Class<? extends Entity> entityClass(String className) {
        try {
            Class<?> found = Class.forName(className);
            return Entity.class.isAssignableFrom(found) ? found.asSubclass(Entity.class) : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
