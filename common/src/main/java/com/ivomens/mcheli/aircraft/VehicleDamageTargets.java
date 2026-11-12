package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.content.def.weapon.DamageFactors;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/**
 * The vehicle classes a weapon file's named damage targets ({@code Helicopter}, {@code Plane}, ...) stand for.
 * The set-up code hands them to the weapons, which know no vehicle class themselves.
 */
public final class VehicleDamageTargets {

    /** Target name to the exact entity class its factor applies to. */
    public static final Map<String, Class<? extends Entity>> BY_NAME = Map.of(
            DamageFactors.HELICOPTER, HelicopterEntity.class,
            DamageFactors.PLANE, PlaneEntity.class,
            DamageFactors.SHIP, ShipEntity.class,
            DamageFactors.TANK, TankEntity.class,
            DamageFactors.VEHICLE, GroundVehicleEntity.class);

    private VehicleDamageTargets() {
    }
}
