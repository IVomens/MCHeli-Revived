package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.Shooter;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A crew member firing one of the vehicle's weapons. Muzzles sit on the hull or on the turret, or, for
 * a ground vehicle aiming its detached turret, follow the gunner's view; a vehicle that limits its
 * turret's arc (a tank) clamps every shot to it.
 */
final class VehicleShooter implements Shooter {

    private final AircraftEntity vehicle;
    private final WeaponComponent weapons;
    private final CrewAimComponent aim;
    private final SeatsComponent seats;
    @Nullable
    private final Entity user;

    VehicleShooter(AircraftEntity vehicle, WeaponComponent weapons, CrewAimComponent aim, SeatsComponent seats,
            @Nullable Entity user) {
        this.vehicle = vehicle;
        this.weapons = weapons;
        this.aim = aim;
        this.seats = seats;
        this.user = user;
    }

    @Override
    @Nullable
    public Entity user() {
        return this.user;
    }

    @Override
    public Entity platform() {
        return this.vehicle;
    }

    @Override
    public AircraftEntity vehicle() {
        return this.vehicle;
    }

    @Override
    public Vec3 muzzleOffset(WeaponMount mount) {
        if (this.weapons.aimsInWorldSpace() && this.aim.isDetachedActive()) {
            return this.weapons.shot().muzzleOffset(mount.position(), null);
        }
        if (mount.onTurret()) {
            return this.seats.turretOffset(mount.position());
        }
        return Rotations.rotate(mount.position(), -this.vehicle.getYRot(), -this.vehicle.getXRot(),
                -this.vehicle.roll());
    }

    @Override
    public Vec2 barrelAim(WeaponGroup group, WeaponMount mount) {
        float[] direction = this.weapons.shot().direction(this.user);
        return new Vec2(direction[0], direction[1]);
    }

    /** The turret arc of a vehicle that limits it, measured from the hull's pitch along the fire direction. */
    @Override
    public Vec2 clampAim(WeaponMount mount, float yaw, float pitch, boolean limitYaw, boolean seatPitch) {
        VehicleSpec info = this.vehicle.spec();
        if (!this.weapons.clampsAimToTurret() || info == null) {
            return new Vec2(yaw, pitch);
        }
        VehicleParts.Weapon weapon = info.armament().getWeaponById(this.weapons.currentId(this.user));
        float minPitch;
        float maxPitch;
        if (seatPitch) {
            // the vehicle's own seat, which it has none of: the vehicle's pitch limits apply
            var seat = this.seats.info(this.vehicle);
            minPitch = seat == null ? info.file().physics().rotationLimits().minPitch() : seat.minPitch;
            maxPitch = seat == null ? info.file().physics().rotationLimits().maxPitch() : seat.maxPitch;
        } else {
            minPitch = weapon == null ? info.file().physics().rotationLimits().minPitch() : weapon.minPitch;
            maxPitch = weapon == null ? info.file().physics().rotationLimits().maxPitch() : weapon.maxPitch;
        }
        float relativeYawRad = (float) Math.toRadians(Mth.wrapDegrees(this.vehicle.getYRot() - yaw));
        float hullPitch = this.vehicle.getXRot() * Mth.cos(relativeYawRad) - this.vehicle.roll() * Mth.sin(relativeYawRad);
        if (limitYaw) {
            float yawLimit = weapon == null ? 360.0F : weapon.maxYaw;
            float relativeYaw = Mth.clamp(Mth.wrapDegrees(yaw - this.vehicle.getYRot()), -yawLimit, yawLimit);
            yaw = this.vehicle.getYRot() + relativeYaw;
        }
        pitch = Mth.clamp(pitch, hullPitch + minPitch, hullPitch + maxPitch);
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        return new Vec2(yaw, pitch);
    }
}
