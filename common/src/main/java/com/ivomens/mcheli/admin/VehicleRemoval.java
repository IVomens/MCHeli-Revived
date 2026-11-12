package com.ivomens.mcheli.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes vehicles for an operator: everyone aboard gets off first, the vehicle and its seats go, and it
 * drops nothing unless {@code RemoveDropsItem} is set (then its item and its cargo). Every removal is
 * logged with who asked for it.
 */
public final class VehicleRemoval {

    private VehicleRemoval() {
    }

    /** Removes the vehicle; {@code by} names who asked, for the log. Returns false if it was already gone. */
    public static boolean remove(AircraftEntity vehicle, String by) {
        if (vehicle.isRemoved()) {
            return false;
        }
        List<Entity> riders = new ArrayList<>(vehicle.getPassengers());
        riders.addAll(vehicle.seats().passengers());
        riders.forEach(Entity::stopRiding);
        boolean drop = McheliConfig.admin().removeDropsItem();
        if (drop && vehicle.spec() != null) {
            Item item = VehicleTypes.item(vehicle.spec()).orElse(null);
            if (item != null) {
                vehicle.spawnAtLocation(new ItemStack(item), 0.0F);
            }
        }
        ModLogger.log("{} removed vehicle {} ({}) owned by {} at {} {} {} in {}; {} rider(s) ejected", by,
                vehicle.getName().getString(), vehicle.getUUID(),
                vehicle.ownership().hasOwner() ? vehicle.ownership().ownerName() : "nobody", vehicle.getBlockX(),
                vehicle.getBlockY(), vehicle.getBlockZ(), vehicle.level().dimension().location(), riders.size());
        vehicle.lifecycle().destroy(drop);
        return true;
    }
}
