package com.ivomens.mcheli.neoforge;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** A creative tab that asks for its icon on every draw instead of caching the first one. */
final class LiveIconCreativeTab extends CreativeModeTab {

    private final Supplier<ItemStack> icon;

    LiveIconCreativeTab(CreativeModeTab.Builder builder, Supplier<ItemStack> icon) {
        super(builder);
        this.icon = icon;
    }

    @Override
    public @NotNull ItemStack getIconItem() {
        return this.icon.get();
    }
}
