package com.ivomens.mcheli.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * A creative tab that asks for its icon on every draw instead of caching the first one. The tab's
 * place in the creative screen is given by Fabric's tab pages, so the row and column here are unused.
 * The constructor is opened by the access widener.
 */
final class LiveIconCreativeTab extends CreativeModeTab {

    private final Supplier<ItemStack> icon;

    LiveIconCreativeTab(Component title, Supplier<ItemStack> icon, DisplayItemsGenerator items) {
        super(Row.TOP, 0, Type.CATEGORY, title, icon, items);
        this.icon = icon;
    }

    @Override
    public @NotNull ItemStack getIconItem() {
        return this.icon.get();
    }
}
