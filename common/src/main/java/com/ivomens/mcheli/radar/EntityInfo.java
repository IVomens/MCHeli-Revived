package com.ivomens.mcheli.radar;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.world.entity.Entity;

/**
 * One radar contact as the server snapshots it ({@link RadarContactSync}) and the client receives it: where the
 * entity is and was a tick before, and what it is.
 *
 * @param entityId        the entity's id in its level
 * @param worldName       the dimension id of the entity's level, e.g. {@code minecraft:overworld}
 * @param entityName      the vehicle's type name, or the entity's display name
 * @param kind            what the entity is, which the instruments tell vehicles, decoys and missiles by
 * @param lastUpdateTime  when the snapshot was taken, in milliseconds of the machine that took it
 */
public record EntityInfo(int entityId, String worldName, String entityName, RadarContactKind kind, double x,
                         double y, double z, double prevX, double prevY, double prevZ, long lastUpdateTime) {

    /** A contact snapshotted now. */
    public static EntityInfo seenNow(int entityId, String worldName, String entityName, RadarContactKind kind,
                                     double x, double y, double z, double prevX, double prevY, double prevZ) {
        return new EntityInfo(entityId, worldName, entityName, kind, x, y, z, prevX, prevY, prevZ,
                System.currentTimeMillis());
    }

    /** A snapshot of {@code e} as it is now. */
    public static EntityInfo of(Entity e) {
        String name = e.getName().getString();
        if (e instanceof AircraftEntity ac && ac.spec() != null) {
            name = ac.spec().name();
        }
        return seenNow(e.getId(), e.level().dimension().location().toString(), name, kindOf(e),
                e.getX(), e.getY(), e.getZ(), e.xOld, e.yOld, e.zOld);
    }

    /**
     * What {@code e} is on the radar. Ships are built on the plane class, so they are told apart first;
     * drones and vertical take-off planes are the vehicle kind they are built as. A projectile is an
     * air-to-air missile when an {@code aamissile} weapon fired it, which always launches the
     * {@code mcheli:aa_missile} kind.
     */
    public static RadarContactKind kindOf(Entity e) {
        return switch (e) {
            case ShipEntity ignored -> RadarContactKind.SHIP;
            case AbstractPlaneEntity ignored -> RadarContactKind.PLANE;
            case HelicopterEntity ignored -> RadarContactKind.HELICOPTER;
            case TankEntity ignored -> RadarContactKind.TANK;
            case GroundVehicleEntity ignored -> RadarContactKind.GROUND_VEHICLE;
            case ChaffEntity ignored -> RadarContactKind.CHAFF;
            case FlareEntity ignored -> RadarContactKind.FLARE;
            case ProjectileEntity projectile when projectile.kind() == ProjectileKind.AA_MISSILE ->
                    RadarContactKind.AIR_TO_AIR_MISSILE;
            case ProjectileEntity projectile when projectile.kind().isGuidedMissile() ->
                    RadarContactKind.OTHER_MISSILE;
            default -> RadarContactKind.OTHER;
        };
    }

    public double getDistanceToEntity(Entity e) {
        return Math.sqrt(this.getDistanceSqToEntity(e));
    }

    public double getDistanceSqToEntity(Entity e) {
        double dx = e.getX() - this.x;
        double dy = e.getY() - this.y;
        double dz = e.getZ() - this.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double getHorizonalDistanceSqToEntity(Entity e) {
        double dx = e.getX() - this.x;
        double dz = e.getZ() - this.z;
        return dx * dx + dz * dz;
    }
}
