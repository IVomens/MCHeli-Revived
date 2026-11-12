package com.ivomens.mcheli.combat;

import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** The invulnerability a weapon hit leaves on a living entity, scaled by the configured factor. */
public final class HurtTime {

    private HurtTime() {
    }

    /** Call right after the hit, while the entity's invulnerable time is still the one the hit set. */
    public static void scale(Entity entity) {
        if (entity instanceof LivingEntity living) {
            living.invulnerableTime = (int) (McheliConfig.combat().hurtResistantTime() * living.invulnerableTime);
        }
    }
}
