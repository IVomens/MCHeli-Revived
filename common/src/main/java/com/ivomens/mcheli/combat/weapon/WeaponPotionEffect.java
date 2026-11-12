package com.ivomens.mcheli.combat.weapon;

import net.minecraft.world.effect.MobEffectInstance;

public class WeaponPotionEffect {

    public final MobEffectInstance potionEffect;
    public final int startDist;
    public final int endDist;

    public WeaponPotionEffect(MobEffectInstance potionEffect, int startDist, int endDist) {
        this.potionEffect = potionEffect;
        this.startDist = startDist;
        this.endDist = endDist;
    }
}
