package com.ivomens.mcheli.ship;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.VehicleSpawnItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class ShipItem extends VehicleSpawnItem<ShipEntity> {

    public ShipItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public VehicleSpec spec() {
        return VehicleTypes.fromItem(VehicleKind.SHIP, this).orElse(null);
    }

    @Override
    protected ShipEntity newEntity(Level level) {
        return new ShipEntity(level);
    }
}
