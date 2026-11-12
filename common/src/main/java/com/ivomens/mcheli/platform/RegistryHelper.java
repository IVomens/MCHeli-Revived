package com.ivomens.mcheli.platform;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Registers MCHeli's fixed content (entity types, menus, block entities, ...) in the {@code mcheli}
 * namespace. Entries are created lazily, when the loader opens the registry, so the returned supplier
 * must not be read before registration has finished.
 */
public interface RegistryHelper {

    <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory);

    /**
     * Creates, without registering, a creative tab that calls {@code icon} each time the icon is drawn,
     * so the icon can change while the game runs.
     */
    CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon,
                                      CreativeModeTab.DisplayItemsGenerator items);
}
