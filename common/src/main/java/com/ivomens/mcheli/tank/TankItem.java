package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.VehicleSpawnItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class TankItem extends VehicleSpawnItem<TankEntity> {

    public TankItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public VehicleSpec spec() {
        return VehicleTypes.fromItem(VehicleKind.TANK, this).orElse(null);
    }

    @Override
    protected TankEntity newEntity(Level level) {
        return new TankEntity(level);
    }
}
