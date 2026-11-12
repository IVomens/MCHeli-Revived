package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.VehicleSpawnItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class PlaneItem extends VehicleSpawnItem<PlaneEntity> {

    public PlaneItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public VehicleSpec spec() {
        return VehicleTypes.fromItem(VehicleKind.PLANE, this).orElse(null);
    }

    @Override
    protected PlaneEntity newEntity(Level level) {
        return new PlaneEntity(level);
    }
}
