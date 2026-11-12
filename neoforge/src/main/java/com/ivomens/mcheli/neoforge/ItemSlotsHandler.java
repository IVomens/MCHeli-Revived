package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.entity.ItemSlots;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** {@link ItemSlots} as a NeoForge item handler, for hoppers, pipes and other mods' inventories. */
record ItemSlotsHandler(ItemSlots slots) implements IItemHandlerModifiable {

    @Override
    public int getSlots() {
        return this.slots.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.slots.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        this.slots.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return this.slots.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.slots.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.slots.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.slots.isItemValid(slot, stack);
    }
}
