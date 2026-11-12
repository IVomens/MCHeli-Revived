package com.ivomens.mcheli.fabric.extension;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * NeoForge's "no repair" item flag, added to {@code Item} and {@code Item.Properties} by mixins. Items
 * made from properties marked through {@code GameplayHooks.noRepair} cannot be combined in the
 * crafting grid or the grindstone.
 */
public interface RepairableItem {

    boolean mcheli$isRepairable();

    /** NeoForge's {@code ItemStack.isRepairable}: the item is not marked "no repair" and the stack has a durability. */
    static boolean isRepairable(ItemStack stack) {
        return ((RepairableItem) stack.getItem()).mcheli$isRepairable() && stack.has(DataComponents.MAX_DAMAGE);
    }

    interface Properties {

        void mcheli$setNoRepair();

        boolean mcheli$isNoRepair();
    }
}
