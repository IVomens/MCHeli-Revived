package com.ivomens.mcheli.mixin;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.combat.projectile.HomingProjectiles;
import com.ivomens.mcheli.combat.weapon.TargetDesignation;
import com.ivomens.mcheli.multiplay.TeamTargetDesignation;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every level its own {@link AircraftIndex}, {@link CombatRandom}, {@link TargetDesignation} and
 * {@link HomingProjectiles}, so they go away with the level and nothing about them is global.
 */
@Mixin(Level.class)
public abstract class MixinLevel implements AircraftIndex.Holder, CombatRandom.Holder, TargetDesignation.Holder,
        HomingProjectiles.Holder {

    @Unique
    private final AircraftIndex mcheli$aircraftIndex = new AircraftIndex();

    @Unique
    private final CombatRandom mcheli$combatRandom = new CombatRandom();

    @Unique
    private final TargetDesignation mcheli$targetDesignation = new TeamTargetDesignation();

    @Unique
    private final HomingProjectiles mcheli$homingProjectiles = new HomingProjectiles();

    @Override
    public HomingProjectiles mcheli$homingProjectiles() {
        return this.mcheli$homingProjectiles;
    }

    @Override
    public AircraftIndex mcheli$aircraftIndex() {
        return this.mcheli$aircraftIndex;
    }

    @Override
    public CombatRandom mcheli$combatRandom() {
        return this.mcheli$combatRandom;
    }

    @Override
    public TargetDesignation mcheli$targetDesignation() {
        return this.mcheli$targetDesignation;
    }
}
