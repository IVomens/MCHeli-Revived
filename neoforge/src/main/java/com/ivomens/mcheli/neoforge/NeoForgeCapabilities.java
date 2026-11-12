package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.block.ModBlockEntities;
import com.ivomens.mcheli.entity.InventoryEntity;
import com.ivomens.mcheli.platform.FluidTank;
import com.ivomens.mcheli.world.WorldEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Offers MCHeli's inventories, fuel tanks and the charger to other mods: the entity inventories as
 * item handlers, aircraft fuel tanks as fluid handlers and the charger as an energy storage.
 */
final class NeoForgeCapabilities {

    private NeoForgeCapabilities() {
    }

    static void register(RegisterCapabilitiesEvent event) {
        registerAircraft(event, AircraftEntityTypes.HELI.get());
        registerAircraft(event, AircraftEntityTypes.PLANE.get());
        registerAircraft(event, AircraftEntityTypes.SHIP.get());
        registerAircraft(event, AircraftEntityTypes.TANK.get());
        registerAircraft(event, AircraftEntityTypes.VEHICLE.get());
        registerInventory(event, WorldEntityTypes.CONTAINER.get());
        registerInventory(event, WorldEntityTypes.UAV_STATION.get());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CHARGER.get(),
                (charger, side) -> new NeoForgeEnergyHelper.ToNeoForge(charger));
    }

    private static <T extends AircraftEntity> void registerAircraft(RegisterCapabilitiesEvent event, EntityType<T> type) {
        event.registerEntity(Capabilities.FluidHandler.ENTITY, type, (aircraft, side) -> {
            FluidTank tank = aircraft.fuel().fluidTank();
            return tank != null ? new FluidTankHandler(tank) : null;
        });
        registerInventory(event, type);
    }

    private static <T extends InventoryEntity> void registerInventory(RegisterCapabilitiesEvent event, EntityType<T> type) {
        // the entity may swap its slots when resized, so wrap the current ones on every query
        event.registerEntity(Capabilities.ItemHandler.ENTITY, type, (entity, context) -> new ItemSlotsHandler(entity.getInventory()));
    }
}
