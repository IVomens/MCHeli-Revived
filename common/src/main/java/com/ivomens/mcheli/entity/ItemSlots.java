package com.ivomens.mcheli.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A fixed number of item slots with simulate-able insert and extract, used for entity inventories.
 * Saved as {@code {Items: [{Slot: int, ...stack}], Size: int}}, the layout older versions wrote, so
 * existing worlds load unchanged. Loader glue exposes it to pipes and hoppers.
 */
public class ItemSlots {

    private final NonNullList<ItemStack> stacks;
    private final Runnable onChanged;

    public ItemSlots(int size, Runnable onChanged) {
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        this.onChanged = onChanged;
    }

    public int getSlots() {
        return this.stacks.size();
    }

    public ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        return this.stacks.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        this.stacks.set(slot, stack);
        this.onChanged.run();
    }

    public int getSlotLimit(int slot) {
        return Item.ABSOLUTE_MAX_STACK_SIZE;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    /**
     * Puts as much of {@code stack} into the slot as fits.
     *
     * @return what did not fit; the passed stack itself when nothing did
     */
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        validateSlotIndex(slot);
        ItemStack existing = this.stacks.get(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(stack, existing)) {
                return stack;
            }
            limit -= existing.getCount();
        }
        if (limit <= 0) {
            return stack;
        }
        boolean reachedLimit = stack.getCount() > limit;
        if (!simulate) {
            if (existing.isEmpty()) {
                this.stacks.set(slot, reachedLimit ? stack.copyWithCount(limit) : stack);
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            this.onChanged.run();
        }
        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
    }

    /** Takes up to {@code amount} items (at most one full stack) out of the slot. */
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }
        validateSlotIndex(slot);
        ItemStack existing = this.stacks.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int toExtract = Math.min(amount, existing.getMaxStackSize());
        if (existing.getCount() <= toExtract) {
            if (simulate) {
                return existing.copy();
            }
            this.stacks.set(slot, ItemStack.EMPTY);
            this.onChanged.run();
            return existing;
        }
        if (!simulate) {
            this.stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract));
            this.onChanged.run();
        }
        return existing.copyWithCount(toExtract);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < this.stacks.size(); i++) {
            if (!this.stacks.get(i).isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                list.add(this.stacks.get(i).save(registries, itemTag));
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", list);
        tag.putInt("Size", this.stacks.size());
        return tag;
    }

    /**
     * Loads the slots written by {@link #save}. The size stays as constructed: entries for slots
     * that do not exist are skipped. Does not count as a change.
     */
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        this.stacks.replaceAll(stack -> ItemStack.EMPTY);
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt("Slot");
            if (slot >= 0 && slot < this.stacks.size()) {
                ItemStack.parse(registries, itemTag).ifPresent(stack -> this.stacks.set(slot, stack));
            }
        }
    }

    private void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= this.stacks.size()) {
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + this.stacks.size() + ")");
        }
    }

    /** A menu slot showing one slot of an {@link ItemSlots}. */
    public static class MenuSlot extends Slot {

        private static final Container EMPTY = new SimpleContainer(0);

        private final ItemSlots slots;
        private final int index;

        public MenuSlot(ItemSlots slots, int index, int x, int y) {
            super(EMPTY, index, x, y);
            this.slots = slots;
            this.index = index;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return !stack.isEmpty() && this.slots.isItemValid(this.index, stack);
        }

        @Override
        public @NotNull ItemStack getItem() {
            return this.slots.getStackInSlot(this.index);
        }

        @Override
        public void set(@NotNull ItemStack stack) {
            this.slots.setStackInSlot(this.index, stack);
            this.setChanged();
        }

        @Override
        public void onQuickCraft(@NotNull ItemStack oldStack, @NotNull ItemStack newStack) {
        }

        @Override
        public int getMaxStackSize() {
            return this.slots.getSlotLimit(this.index);
        }

        @Override
        public int getMaxStackSize(@NotNull ItemStack stack) {
            return Math.min(stack.getMaxStackSize(), this.slots.getSlotLimit(this.index));
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return !this.slots.extractItem(this.index, 1, true).isEmpty();
        }

        @Override
        public @NotNull ItemStack remove(int amount) {
            return this.slots.extractItem(this.index, amount, false);
        }
    }
}
