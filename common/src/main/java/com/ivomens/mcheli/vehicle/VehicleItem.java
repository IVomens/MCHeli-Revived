package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class VehicleItem extends VehicleSpawnItem<GroundVehicleEntity> {

    public VehicleItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public VehicleSpec spec() {
        return VehicleTypes.fromItem(VehicleKind.VEHICLE, this).orElse(null);
    }

    @Override
    protected GroundVehicleEntity newEntity(Level level) {
        return new GroundVehicleEntity(level);
    }
}
