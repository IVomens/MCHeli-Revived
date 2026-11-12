package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.parachute.ParachuteEntity;
import com.ivomens.mcheli.combat.weapon.CartridgeEntity;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.registry.EntityTypeRegistration;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/** The vehicles, their seats and hit boxes, the spent cartridges they eject and the parachutes of their crews. */
public final class AircraftEntityTypes {

    public static final Supplier<EntityType<HelicopterEntity>> HELI =
            EntityTypeRegistration.vehicle("heli", HelicopterEntity::new);
    public static final Supplier<EntityType<PlaneEntity>> PLANE =
            EntityTypeRegistration.vehicle("plane", PlaneEntity::new);
    public static final Supplier<EntityType<ShipEntity>> SHIP = EntityTypeRegistration.vehicle("ship", ShipEntity::new);
    public static final Supplier<EntityType<TankEntity>> TANK = EntityTypeRegistration.vehicle("tank", TankEntity::new);
    public static final Supplier<EntityType<GroundVehicleEntity>> VEHICLE =
            EntityTypeRegistration.vehicle("vehicle", GroundVehicleEntity::new);
    public static final Supplier<EntityType<SeatEntity>> SEAT =
            EntityTypeRegistration.placedByParent("seat", SeatEntity::new, 1.0F, 1.0F, 16);
    public static final Supplier<EntityType<HitBoxEntity>> HITBOX =
            EntityTypeRegistration.simple("hitbox", HitBoxEntity::new, 1.0F, 1.0F, 10, 10);
    public static final Supplier<EntityType<HideEntity>> HIDE =
            EntityTypeRegistration.simple("hide", HideEntity::new, 1.0F, 1.0F, 10, 10);
    public static final Supplier<EntityType<CartridgeEntity>> CARTRIDGE =
            EntityTypeRegistration.simple("cartridge", CartridgeEntity::new, 0.25F, 0.25F, 4, 20);
    public static final Supplier<EntityType<ParachuteEntity>> PARACHUTE =
            EntityTypeRegistration.simple("parachute", ParachuteEntity::new, 1.5F, 0.6F, 10, 3);

    private AircraftEntityTypes() {
    }

    /** Loading the class registers the entity types; call once while the mod is constructed. */
    public static void init() {
    }
}
