package com.ivomens.mcheli.multiplay;

import com.ivomens.mcheli.combat.weapon.TargetDesignation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Spotter weapons designate for the shooter's scoreboard team, the way the range finder does. */
public final class TeamTargetDesignation implements TargetDesignation {

    @Override
    public boolean markPoint(Player player, double x, double y, double z) {
        return Multiplay.markPoint(player, x, y, z);
    }

    @Override
    public boolean spotEntities(LivingEntity spotter, float roll, double x, double y, double z, int filter,
                                float length, int markTime, float angle) {
        return Multiplay.spotEntity(spotter, roll, x, y, z, filter, length, markTime, angle);
    }
}
