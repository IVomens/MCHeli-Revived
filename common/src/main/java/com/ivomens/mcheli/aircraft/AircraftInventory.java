package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.entity.ItemSlots;
import com.ivomens.mcheli.aircraft.parachute.ParachuteItem;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Fuel and parachute slots of an aircraft (separate from the cargo inventory of InventoryEntity). */
public class AircraftInventory implements VehicleComponent {

    public static final int SLOT_FUEL_IN = 0;
    public static final int SLOT_FUEL_OUT = 1;
    public static final int SLOT_PARACHUTE0 = 3;
    public static final int SLOT_PARACHUTE1 = 4;
    public static final int SIZE = 10;
    private static final int PARACHUTE_SLOTS = 2;

    private final AircraftEntity aircraft;
    private final ItemSlots items;

    public AircraftInventory(AircraftEntity aircraft) {
        this.aircraft = aircraft;
        this.items = new ItemSlots(SIZE, aircraft::setChanged);
    }

    public ItemStack getFuelSlotItemStack(int index) {
        return this.items.getStackInSlot(index);
    }

    public boolean haveParachute() {
        return findParachuteSlot() >= 0;
    }

    public void consumeParachute() {
        int slot = findParachuteSlot();
        if (slot >= 0) {
            this.items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private int findParachuteSlot() {
        for (int slot = SLOT_PARACHUTE0; slot < SLOT_PARACHUTE0 + PARACHUTE_SLOTS; slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ParachuteItem) {
                return slot;
            }
        }
        return -1;
    }

    /** Scatters the slots' contents around the aircraft when it is broken for good. */
    public void dropContents() {
        if (!this.aircraft.dropContentsWhenDead() || this.aircraft.level().isClientSide) {
            return;
        }
        RandomSource random = this.aircraft.getRandom();
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            stack = stack.copy();
            float offsetX = random.nextFloat() * 0.8F + 0.1F;
            float offsetY = random.nextFloat() * 0.8F + 0.1F;
            float offsetZ = random.nextFloat() * 0.8F + 0.1F;
            while (!stack.isEmpty()) {
                ItemStack drop = stack.split(Math.min(random.nextInt(21) + 10, stack.getCount()));
                ItemEntity entity = new ItemEntity(this.aircraft.level(), this.aircraft.getX() + offsetX,
                        this.aircraft.getY() + offsetY, this.aircraft.getZ() + offsetZ, drop);
                entity.setDeltaMovement(random.nextGaussian() * 0.05, random.nextGaussian() * 0.05 + 0.2, random.nextGaussian() * 0.05);
                this.aircraft.level().addFreshEntity(entity);
            }
            this.items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public void writeToNBT(CompoundTag tag) {
        tag.put("ItemsAC", this.items.save(this.aircraft.registryAccess()));
    }

    public void readFromNBT(CompoundTag tag) {
        if (tag.contains("ItemsAC")) {
            // older saves may have stored a different size; the slot count stays SIZE
            this.items.load(this.aircraft.registryAccess(), tag.getCompound("ItemsAC"));
        }
    }

    public ItemSlots getItemHandler() {
        return this.items;
    }

    public String getInventoryName() {
        return this.aircraft.spec() != null ? this.aircraft.spec().name() : "";
    }
}
