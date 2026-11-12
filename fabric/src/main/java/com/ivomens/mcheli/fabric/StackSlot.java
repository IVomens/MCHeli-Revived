package com.ivomens.mcheli.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.minecraft.world.item.ItemStack;

/**
 * One item stack as the slot of a Transfer API item context, the way NeoForge's item capabilities see
 * a stack: what the item's storage writes ends up in {@link #stack()}.
 */
final class StackSlot extends SingleStackStorage {

    private ItemStack stack;

    private StackSlot(ItemStack stack) {
        this.stack = stack;
    }

    /** A context whose only slot starts with {@code stack}; the stack object itself is not changed. */
    static StackSlot of(ItemStack stack) {
        return new StackSlot(stack);
    }

    ContainerItemContext context() {
        return ContainerItemContext.ofSingleSlot(this);
    }

    /** The slot's current contents. */
    ItemStack stack() {
        return this.stack;
    }

    @Override
    protected ItemStack getStack() {
        return this.stack;
    }

    @Override
    protected void setStack(ItemStack stack) {
        this.stack = stack;
    }
}
