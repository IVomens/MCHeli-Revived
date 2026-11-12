package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Item that places one vehicle of type {@code E}, described by the definition registered for this item.
 */
public abstract class VehicleSpawnItem<E extends AircraftEntity> extends AircraftItem {

    protected VehicleSpawnItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    protected abstract E newEntity(Level level);

    @Nullable
    @Override
    public E createAircraft(Level level, double x, double y, double z, ItemStack itemStack) {
        VehicleSpec info = spec();
        if (info == null) {
            ModLogger.log(level, "##### %s: no vehicle definition for %s", getClass().getSimpleName(),
                    getDescriptionId());
            return null;
        }

        E vehicle = newEntity(level);
        vehicle.setPos(x, y, z);
        vehicle.xo = x;
        vehicle.yo = y;
        vehicle.zo = z;
        vehicle.cameras().camera().setPosition(x, y, z);
        vehicle.identity().setTypeName(info.name());
        if (!level.isClientSide) {
            vehicle.appearance().setTexture(VehicleTypes.nextTexture(info));
        }
        return vehicle;
    }
}
