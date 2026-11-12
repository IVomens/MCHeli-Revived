package com.ivomens.mcheli.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * An entity with an item inventory. The inventory is exposed both as a vanilla {@link Container}
 * and, through {@link #getInventory()}, as {@link ItemSlots}, which the loader glue offers to pipes
 * and hoppers (the item handler capability on NeoForge).
 */
public abstract class InventoryEntity extends McheliEntity implements Container {

    private static final short DEFAULT_SIZE = 54;
    private static final double MAX_USE_DISTANCE = 64.0;

    private boolean dropContentsWhenDead = true;
    protected short inventorySize = DEFAULT_SIZE;

    protected ItemSlots inventory = createSlots(inventorySize);

    public boolean dropContentsWhenDead() {
        return this.dropContentsWhenDead;
    }

    public void setDropContentsWhenDead(boolean dropContentsWhenDead) {
        this.dropContentsWhenDead = dropContentsWhenDead;
    }

    public InventoryEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public ItemSlots getInventory() {
        return inventory;
    }

    @Override
    public int getContainerSize() {
        return inventory.getSlots();
    }

    /** Same as {@link #getContainerSize()}. */
    public int getSizeInventory() {
        return getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < inventory.getSlots();
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return isValidSlot(slot) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** Same as {@link #getItem(int)}. */
    public @NotNull ItemStack getStackInSlot(int slot) {
        return getItem(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        return isValidSlot(slot) ? inventory.extractItem(slot, amount, false) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.getStackInSlot(slot);
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        if (isValidSlot(slot)) {
            inventory.setStackInSlot(slot, stack);
        }
    }

    /** Same as {@link #setItem(int, ItemStack)}. */
    public void setInventorySlotContents(int slot, @NotNull ItemStack stack) {
        setItem(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !isRemoved() && player.distanceToSqr(this) <= MAX_USE_DISTANCE * MAX_USE_DISTANCE;
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return true;
    }

    /** Entity inventories are saved with the entity, so there is nothing to mark dirty. */
    @Override
    public void setChanged() {}

    public String getInventoryName() {
        return "Inventory";
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.put("items", inventory.save(this.registryAccess()));
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("items")) {
            // Keeps the configured size even if the saved inventory was smaller or larger.
            inventory.load(this.registryAccess(), tag.getCompound("items"));
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        // Changing dimension or unloading does not destroy the entity, so the contents travel with it.
        if (reason.shouldDestroy() && dropContentsWhenDead && !level().isClientSide) {
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level(), getX(), getY(), getZ(), stack);
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
        super.remove(reason);
    }

    /** Opens the inventory screen; entities with a screen override this. */
    public void displayInventory(Player player) {}

    /** Resizes the inventory, keeping the contents of the slots that still exist. */
    public void setInventorySize(short newSize) {
        if (newSize == this.inventorySize || newSize < 0) {
            return;
        }
        ItemSlots oldHandler = this.inventory;
        ItemSlots newHandler = createSlots(newSize);
        int copySlots = Math.min(oldHandler != null ? oldHandler.getSlots() : 0, newSize);
        for (int i = 0; i < copySlots; i++) {
            ItemStack stack = oldHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                newHandler.setStackInSlot(i, stack.copy());
            }
        }
        this.inventory = newHandler;
        this.inventorySize = newSize;
    }

    private ItemSlots createSlots(int size) {
        return new ItemSlots(size, this::setChanged);
    }
}
