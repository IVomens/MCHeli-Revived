package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.entityview.AiGunner;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** Creative players, and gunners placed by one, use ammo and fuel without running out. */
public final class Creative {

    private Creative() {
    }

    public static boolean isCreative(@Nullable Entity entity) {
        return entity instanceof Player player && player.getAbilities().instabuild
                || entity instanceof AiGunner gunner && gunner.isCreative();
    }
}
