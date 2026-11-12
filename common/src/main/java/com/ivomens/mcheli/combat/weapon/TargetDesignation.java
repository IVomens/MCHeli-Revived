package com.ivomens.mcheli.combat.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Shares what a spotter weapon designates with the shooter's team: a marked block, or the entities spotted
 * in a cone. Team play is a feature above the weapons, so every level is given its implementation (see
 * {@link Holder}); the weapons only ask for it. Fire modes run on both sides, so the client level has one
 * too: there it only answers whether something would be designated.
 */
public interface TargetDesignation {

    /** The designation of the level. */
    static TargetDesignation of(Level level) {
        return ((Holder) level).mcheli$targetDesignation();
    }

    /**
     * Marks the block the player looks at from {@code (x, y, z)} for the team, or clears the mark when the
     * player looks at nothing.
     *
     * @return whether a block was marked
     */
    boolean markPoint(Player player, double x, double y, double z);

    /**
     * Spots the entities the filter accepts inside a cone in front of the spotter and shows them to the team
     * for {@code markTime} seconds.
     *
     * @param roll the bank of the vehicle the spotter looks out of, which tilts the cone; 0 on foot
     * @return whether anything was spotted
     */
    boolean spotEntities(LivingEntity spotter, float roll, double x, double y, double z, int filter, float length,
                         int markTime, float angle);

    /** Implemented on {@link Level} by a mixin, so every level owns its designation. */
    interface Holder {
        TargetDesignation mcheli$targetDesignation();
    }
}
