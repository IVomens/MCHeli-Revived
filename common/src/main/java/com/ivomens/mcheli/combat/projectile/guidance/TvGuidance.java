package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.entityview.Riders;
import com.ivomens.mcheli.combat.projectile.FlightLimits;
import com.ivomens.mcheli.combat.projectile.FlightModel;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponKind;
import com.ivomens.mcheli.combat.weapon.WeaponRayUtil;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * TV guided missile. A wire guided one flies where its shooter looks while the shooter watches through
 * the missile camera; a laser guided one homes on the point the shooter (or its designator pod) aims
 * at. The missile goes away when its shooter does or falls too far behind.
 * <p>
 * <b>The designator is never switched on.</b> A laser guided TV weapon's own designator has no way to
 * be turned on, so while the shooter keeps that weapon selected its missile flies straight on; it only
 * follows the laser once the shooter selects another weapon (or fires without a vehicle).
 */
public final class TvGuidance implements Guidance {

    private static final double LASER_RANGE = 1500.0;
    /** Ballistic laser guided missiles only steer a little vertically and let gravity do the rest. */
    private static final double BALLISTIC_VERTICAL_AIM = 0.3;

    @Override
    public void steer(ProjectileEntity missile) {
        Entity shooter = missile.shooter();
        if (shooter == null) {
            if (!missile.level().isClientSide) missile.discard();
            return;
        }
        int maxRange = FlightLimits.steeredMissileRange();
        if (maxRange >= 0 && missile.distanceToSqr(shooter) > (double) maxRange * maxRange) {
            missile.discard();
        }
        if (!missile.level().isClientSide && !missile.isRemoved() && !shooter.isRemoved()) {
            ArmedVehicle vehicle = Riders.vehicleOf(shooter) instanceof ArmedVehicle armed ? armed : null;
            if (missile.spec().missile().laserGuidance()) {
                steerToLaser(missile, shooter, vehicle, missile.spec());
            } else {
                steerAlongView(missile, shooter, vehicle);
            }
        }
    }

    /** Flies along the shooter's view while the vehicle is still watching this missile. */
    private static void steerAlongView(ProjectileEntity missile, Entity shooter, @Nullable ArmedVehicle vehicle) {
        if (vehicle == null || vehicle.cameras().tvMissile() != missile) {
            return;
        }
        float yaw = shooter.getYRot();
        float pitch = shooter.getXRot();
        double cosPitch = Math.cos(Math.toRadians(pitch));
        missile.setMotionAlong(-Math.sin(Math.toRadians(yaw)) * cosPitch, -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * cosPitch);
        missile.hasImpulse = true;
        missile.setYRot(yaw);
    }

    private static void steerToLaser(ProjectileEntity missile, Entity shooter, @Nullable ArmedVehicle vehicle,
                                     WeaponSpec info) {
        if (vehicle != null && holdsLaserGuidedTvWeapon(vehicle, shooter)) {
            return;
        }
        Entity aimSource = info.missile().hasLaserGuidancePod() || missile.platform() == null ? shooter
                : missile.platform();
        Vec3 src = laserSource(shooter, vehicle);
        Vec3 dst = src.add(Vec3.directionFromRotation(aimSource.getXRot(), aimSource.getYRot()).scale(LASER_RANGE));
        missile.aimAt(WeaponRayUtil.clipLoadedOrEnd(missile.level(), src, dst, missile));
        steerToLaserSpot(missile, info);
    }

    private static boolean holdsLaserGuidedTvWeapon(ArmedVehicle vehicle, Entity shooter) {
        WeaponGroup current = vehicle.weapons().current(shooter);
        return current != null && current.currentMount() != null && current.kind() == WeaponKind.TV_MISSILE
                && current.spec().missile().laserGuidance();
    }

    /** The laser starts at the UAV itself, or at the shooter's eyes, half a tick back. */
    private static Vec3 laserSource(Entity shooter, @Nullable ArmedVehicle vehicle) {
        Entity origin = vehicle != null && vehicle.isUAV() ? vehicle.entity() : shooter;
        double x = origin.xo + (origin.getX() - origin.xo) * 0.5;
        double y = origin.yo + (origin.getY() - origin.yo) * 0.5;
        double z = origin.zo + (origin.getZ() - origin.zo) * 0.5;
        return new Vec3(x, y + (origin == shooter ? origin.getEyeHeight() : 0), z);
    }

    /** Steers at the laser spot when it lies on a solid block (in a loaded chunk). */
    private static void steerToLaserSpot(ProjectileEntity missile, WeaponSpec info) {
        Vec3 spot = missile.targetPoint();
        BlockPos spotPos = BlockPos.containing(spot);
        if (missile.level().hasChunkAt(spotPos) && missile.level().getBlockState(spotPos).isSolid()) {
            double dx = spot.x - missile.getX();
            double dy = spot.y - missile.getY();
            double dz = spot.z - missile.getZ();
            Vec3 m = missile.getDeltaMovement();
            if (FlightModel.gravity(missile, info) == 0.0) {
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 0.001) return;
                float turn = (float) info.missile().turningFactor();
                missile.setDeltaMovement(FlightModel.steer(missile, m, dx / distance, dy / distance, dz / distance,
                        info.ballistics().acceleration(), turn));
            } else {
                dy *= BALLISTIC_VERTICAL_AIM;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 0.001) return;
                missile.setDeltaMovement(dx * missile.speed() / distance, m.y, dz * missile.speed() / distance);
            }
            missile.hasImpulse = true;
        }
        FlightModel.faceMotion(missile);
    }
}
