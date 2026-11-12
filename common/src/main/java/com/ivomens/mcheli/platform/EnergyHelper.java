package com.ivomens.mcheli.platform;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Energy stored in items from other mods (batteries), when the loader has an energy API. */
public interface EnergyHelper {

    /** The item's energy storage, or null when it has none. Writes go to the stack. */
    @Nullable
    EnergyStore getItemEnergy(ItemStack stack);
}
