package com.ivomens.mcheli.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Consumer;

/** Menus whose client side is built from extra data the server writes when opening them. */
public interface MenuHelper {

    /** A menu type whose client-side menu is created from the data written by {@link #openMenu}. */
    <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory);

    /**
     * Opens the menu and hands what {@code extraData} writes to the client's {@link MenuFactory}. Menus of
     * a type made by {@link #createMenuType} always open through here, even when they need no extra data:
     * Fabric refuses to open them with the vanilla {@code openMenu}.
     */
    void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData);

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData);
    }
}
