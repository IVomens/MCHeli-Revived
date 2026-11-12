package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.VehicleSpawnItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class HelicopterItem extends VehicleSpawnItem<HelicopterEntity> {

    public HelicopterItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public VehicleSpec spec() {
        return VehicleTypes.fromItem(VehicleKind.HELICOPTER, this).orElse(null);
    }

    @Override
    protected HelicopterEntity newEntity(Level level) {
        return new HelicopterEntity(level);
    }
}
