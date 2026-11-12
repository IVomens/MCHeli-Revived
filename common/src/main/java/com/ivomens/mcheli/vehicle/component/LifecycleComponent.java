package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.vehicle.settings.SupplyRules;
import com.ivomens.mcheli.vehicle.uav.UAVTracker;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/**
 * The vehicle's life from spawn to removal: its age in ticks, the infinite supplies the config grants it,
 * and what goes with it when it is destroyed (its cargo, seats and tow chain, and a UAV's registration).
 */
public final class LifecycleComponent implements VehicleComponent {

    /** The age starts again at 1 here, so it never overflows. */
    private static final int AGE_WRAP = 1_000_000;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final SeatsComponent seats;
    private final EngineSoundComponent sound;
    private final TowComponent tow;
    private final AircraftInventory cargo;
    private final Supplier<SupplyRules> supplies;
    private int age;

    public LifecycleComponent(AircraftEntity vehicle, StatusWord status, SeatsComponent seats,
            EngineSoundComponent sound, TowComponent tow, AircraftInventory cargo, Supplier<SupplyRules> supplies) {
        this.vehicle = vehicle;
        this.status = status;
        this.seats = seats;
        this.sound = sound;
        this.tow = tow;
        this.cargo = cargo;
        this.supplies = supplies;
    }

    /** Ticks lived, counted halfway through each tick (see the tick pipeline); 0 before the first. */
    public int age() {
        return this.age;
    }

    @Override
    public void describe(StateSink state) {
        state.put("age", this.age);
        state.put("infinite ammo", this.status.get(StatusWord.INFINITE_AMMO));
    }

    public void countTick() {
        this.age++;
        if (this.age >= AGE_WRAP) {
            this.age = 1;
        }
    }

    /**
     * Server: the infinite ammo and fuel settings, taken when the vehicle is made and again on its first
     * tick, so a vehicle loaded from a save follows the current config.
     */
    public void applyInfiniteSupplies() {
        if (this.age == 0) {
            SupplyRules supplies = this.supplies.get();
            this.status.set(StatusWord.INFINITE_AMMO, supplies.infiniteAmmo());
            this.status.set(StatusWord.INFINITE_FUEL, supplies.infiniteFuel());
        }
    }

    /** Destroys the vehicle for good; with {@code dropCargo} its cargo inventory drops as well. */
    public void destroy(boolean dropCargo) {
        this.vehicle.setDropContentsWhenDead(dropCargo);
        this.vehicle.discard();
    }

    /** Throws the pilot out and destroys the vehicle with its cargo, as a UAV's self-destruct charge does. */
    public void destruct() {
        Entity pilot = this.vehicle.getRiddenByEntity();
        if (pilot != null) {
            pilot.stopRiding();
        }
        this.destroy(true);
    }

    /** The vehicle is about to be removed: a pilot riding it gets off first if it is destroyed. */
    public void beforeRemoval(Entity.RemovalReason reason) {
        Entity pilot = this.vehicle.getRiddenByEntity();
        if (reason.shouldDestroy() && pilot != null && pilot.getVehicle() == this.vehicle) {
            pilot.stopRiding();
        }
    }

    /**
     * The vehicle was removed. Destroyed, it leaves its cargo and takes its seats and tow chain with it;
     * unloaded or moved to another dimension, they stay with the saved entity.
     */
    public void afterRemoval(Entity.RemovalReason reason) {
        if (!reason.shouldDestroy()) {
            return;
        }
        this.cargo.dropContents();
        this.seats.discardAll();
        this.sound.play();
        this.tow.discardChain();
        if (this.vehicle.isUAV() && !this.vehicle.level().isClientSide) {
            UAVTracker.delUAVPos(this.vehicle.level(), this.vehicle);
        }
    }
}
