package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.EnergyHelper;
import com.ivomens.mcheli.platform.EnergyStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Item energy through Team Reborn Energy, the energy API Fabric's tech mods share. It is an optional
 * dependency: without it no item has energy and the charger accepts none from cables, which is what
 * NeoForge does when no mod provides energy items. The API's classes are only touched when the mod is
 * installed (see {@link TeamRebornEnergy}).
 */
public class FabricEnergyHelper implements EnergyHelper {

    static final String TEAM_REBORN_ENERGY = "team_reborn_energy";

    private final boolean available = FabricLoader.getInstance().isModLoaded(TEAM_REBORN_ENERGY);

    @Nullable
    @Override
    public EnergyStore getItemEnergy(ItemStack stack) {
        return this.available ? TeamRebornEnergy.itemEnergy(stack) : null;
    }

    /** Offers the charger to cables when Team Reborn Energy is installed; called once the registries are filled. */
    void registerBlockEnergy() {
        if (this.available) {
            TeamRebornEnergy.registerCharger();
        }
    }
}
