package com.ivomens.mcheli.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Player inventory checks the vanilla inventory has no single call for. */
public final class Inventories {

    private Inventories() {
    }

    /** Whether the inventory holds at least one of the item. */
    public static boolean hasItem(Player player, Item item) {
        return item != null && player.getInventory().contains(new ItemStack(item));
    }

    /** Removes one of the item from the inventory; whether one was there to remove. */
    public static boolean removeOne(Player player, Item item) {
        int index = player.getInventory().findSlotMatchingUnusedItem(new ItemStack(item));
        if (index < 0) {
            return false;
        }
        return !player.getInventory().removeItem(index, 1).isEmpty();
    }
}
