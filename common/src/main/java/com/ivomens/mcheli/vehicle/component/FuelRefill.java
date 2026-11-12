package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.aircraft.FuelItem;
import com.ivomens.mcheli.compat.energy.EnergyCompat;
import com.ivomens.mcheli.platform.FluidHelper;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.registry.McheliCriteria;
import com.ivomens.mcheli.vehicle.VehicleMotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Refilling the tank from the fuel slot of the inventory: fluid containers, fuel cans, and for
 * electric vehicles a battery. Empty containers go to the output slot.
 */
final class FuelRefill {

    private static final int REFUEL_INTERVAL = 10;

    private final AircraftEntity vehicle;
    private final LifecycleComponent lifecycle;
    private final AircraftInventory cargo;
    private final FuelComponent fuel;

    FuelRefill(AircraftEntity vehicle, FuelComponent fuel, LifecycleComponent lifecycle, AircraftInventory cargo) {
        this.vehicle = vehicle;
        this.lifecycle = lifecycle;
        this.cargo = cargo;
        this.fuel = fuel;
    }

    boolean hasPendingWork() {
        return this.fuel.capacity() > 0 && this.fuel.amount() < this.fuel.capacity()
                && !this.cargo.getFuelSlotItemStack(AircraftInventory.SLOT_FUEL_IN).isEmpty();
    }

    void tick() {
        if (this.fuel.isElectric()) {
            // no fluid tank: top up from an onboard battery, which also works in flight
            chargeFromBattery();
        } else {
            refuelFromSlot();
        }
    }

    private void refuelFromSlot() {
        AircraftEntity ac = this.vehicle;
        int currentFuel = this.fuel.amount();
        int maxFuel = this.fuel.capacity();
        if (currentFuel >= maxFuel || this.lifecycle.age() % REFUEL_INTERVAL != 0 || !VehicleMotion.isParkedForSupply(ac)) {
            return;
        }

        AircraftInventory inventory = this.cargo;
        ItemStack input = inventory.getFuelSlotItemStack(AircraftInventory.SLOT_FUEL_IN);
        if (input.isEmpty()) {
            return;
        }
        if (input.getItem() instanceof FuelItem) {
            refuelFromFuelItem(inventory, input, currentFuel, maxFuel);
            return;
        }

        // work on a single container so stacked buckets/cans drain one at a time
        FluidHelper.DrainedContainer result = Services.FLUID.drain(input.copyWithCount(1), this.fuel::canAcceptFuel,
                maxFuel - currentFuel);
        if (result == null) {
            return;
        }
        if (putInOutputSlot(inventory, result.container())) {
            input.shrink(1);
            this.fuel.setFuelType(result.drained().fluid());
            this.fuel.setAmount(currentFuel + result.drained().amount());
            triggerRefuelCriteria();
        }
    }

    /** Fuel cans store their contents as durability: the remaining durability is the fuel left. */
    private void refuelFromFuelItem(AircraftInventory inventory, ItemStack stack, int currentFuel, int maxFuel) {
        Fluid fuelType = FuelFluids.byName(FuelFluids.DEFAULT_FUEL_TYPE);
        if (!this.fuel.canAcceptFuel(fuelType)) {
            return;
        }
        int fuelInItem = stack.getMaxDamage() - stack.getDamageValue();
        if (fuelInItem <= 0) {
            return;
        }

        int transferred = Math.min(maxFuel - currentFuel, fuelInItem);
        stack.setDamageValue(stack.getDamageValue() + transferred);
        this.fuel.setFuelType(fuelType);
        this.fuel.setAmount(currentFuel + transferred);

        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            ItemStack empty = new ItemStack(stack.getItem());
            empty.setDamageValue(stack.getMaxDamage());
            if (putInOutputSlot(inventory, empty)) {
                stack.shrink(1);
            }
        }
        triggerRefuelCriteria();
    }

    private static boolean putInOutputSlot(AircraftInventory inventory, ItemStack container) {
        ItemStack output = inventory.getFuelSlotItemStack(AircraftInventory.SLOT_FUEL_OUT);
        if (output.isEmpty()) {
            inventory.getItemHandler().setStackInSlot(AircraftInventory.SLOT_FUEL_OUT, container.copy());
            return true;
        }
        if (ItemStack.isSameItemSameComponents(output, container) && output.getCount() < output.getMaxStackSize()) {
            output.grow(container.getCount());
            return true;
        }
        return false;
    }

    /** Electric vehicles: a battery item in the fuel slot acts as reserve storage for the internal buffer. */
    private void chargeFromBattery() {
        if (!this.fuel.allowBattery() || this.lifecycle.age() % REFUEL_INTERVAL != 0) {
            return;
        }
        int needed = this.fuel.capacity() - this.fuel.amount();
        if (needed <= 0) {
            return;
        }
        ItemStack battery = this.cargo.getFuelSlotItemStack(AircraftInventory.SLOT_FUEL_IN);
        if (battery.isEmpty() || !EnergyCompat.isBattery(battery)) {
            return;
        }
        int pulled = EnergyCompat.dischargeFromItem(battery, needed, false);
        if (pulled > 0) {
            this.fuel.setAmount(this.fuel.amount() + pulled);
            triggerRefuelCriteria();
        }
    }

    private void triggerRefuelCriteria() {
        if (this.vehicle.getRiddenByEntity() instanceof ServerPlayer player) {
            McheliCriteria.SUPPLY_FUEL.trigger(player);
        }
    }
}
