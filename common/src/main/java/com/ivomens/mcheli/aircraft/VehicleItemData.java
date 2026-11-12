package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.util.StackData;
import com.ivomens.mcheli.vehicle.component.VehicleNbtCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * What a vehicle keeps when it is picked up as an item and placed again: its destroy command, and
 * with the matching config options its fuel and its damage (as the item's damage bar).
 */
public final class VehicleItemData {

    private VehicleItemData() {
    }

    public static void write(AircraftEntity vehicle, ItemStack stack) {
        StackData.update(stack, nbt -> {
            nbt.putString(VehicleNbtCodec.ITEM_COMMAND, vehicle.wreck().command());
            if (McheliConfig.vehicles().itemFuel()) {
                nbt.putInt(VehicleNbtCodec.ITEM_FUEL, vehicle.fuel().amount());
            }
        });
        if (McheliConfig.vehicles().itemDamage() && stack.isDamageableItem()) {
            stack.setDamageValue(vehicle.damage().damageTaken());
        }
    }

    public static void read(ItemStack stack, AircraftEntity vehicle) {
        if (StackData.hasTag(stack)) {
            CompoundTag nbt = StackData.getTag(stack);
            vehicle.wreck().setCommand(nbt.getString(VehicleNbtCodec.ITEM_COMMAND));
            if (McheliConfig.vehicles().itemFuel()) {
                vehicle.fuel().setAmount(nbt.getInt(VehicleNbtCodec.ITEM_FUEL));
            }
        }
        if (McheliConfig.vehicles().itemDamage() && stack.isDamageableItem()) {
            vehicle.damage().setDamageTaken(stack.getDamageValue());
        }
    }
}
