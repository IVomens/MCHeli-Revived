package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * Where a crew member's current weapon points in the world, and where a muzzle sits. Barrels are
 * aimed relative to the hull, so the weapon's angles are added to the airframe attitude; ground
 * vehicles aim in world space instead (see {@link WorldShotDirection}). Without a weapon the
 * user's own aim counts.
 */
public class ShotDirection {

    protected final AircraftEntity vehicle;
    protected final WeaponComponent weapons;
    protected final CrewAimComponent aim;
    protected final RollComponent roll;
    /** Whether the airframe attitude is a quaternion ({@code ExperimentalQuaternionRotation}). */
    private final BooleanSupplier quaternionAttitude;

    ShotDirection(AircraftEntity vehicle, WeaponComponent weapons, CrewAimComponent aim, RollComponent roll,
            BooleanSupplier quaternionAttitude) {
        this.vehicle = vehicle;
        this.weapons = weapons;
        this.aim = aim;
        this.roll = roll;
        this.quaternionAttitude = quaternionAttitude;
    }

    @Nullable
    private WeaponGroup weaponOf(@Nullable Entity user) {
        WeaponGroup weapon = user != null ? this.weapons.current(user) : null;
        return weapon != null && weapon.currentMount() != null ? weapon : null;
    }

    /** World yaw the user's current weapon points at, or the user's own yaw without a weapon. */
    public float yaw(@Nullable Entity user) {
        WeaponGroup weapon = weaponOf(user);
        if (weapon == null) {
            return this.aim.userYaw(user);
        }
        return Mth.wrapDegrees(this.vehicle.getYRot() + weapon.yaw() + weapon.currentMount().fixedYaw());
    }

    public float yaw(@Nullable Entity user, float partialTicks) {
        WeaponGroup weapon = weaponOf(user);
        if (weapon == null) {
            return this.aim.userYaw(user, partialTicks);
        }
        float weaponYaw = weapon.turret().prevYaw() + (weapon.yaw() - weapon.turret().prevYaw()) * partialTicks;
        return Mth.wrapDegrees(Mth.lerp(partialTicks, this.vehicle.yRotO, this.vehicle.getYRot()) + weaponYaw
                + weapon.currentMount().fixedYaw());
    }

    public float pitch(@Nullable Entity user) {
        WeaponGroup weapon = weaponOf(user);
        if (weapon == null) {
            return this.aim.userPitch(user);
        }
        return Mth.wrapDegrees(this.vehicle.getXRot() + weapon.pitch()
                + weapon.currentMount().fixedPitch());
    }

    public float pitch(@Nullable Entity user, float partialTicks) {
        WeaponGroup weapon = weaponOf(user);
        if (weapon == null) {
            return this.aim.userPitch(user, partialTicks);
        }
        float weaponPitch = weapon.turret().prevPitch() + (weapon.pitch() - weapon.turret().prevPitch()) * partialTicks;
        return Mth.wrapDegrees(Mth.lerp(partialTicks, this.vehicle.xRotO, this.vehicle.getXRot()) + weaponPitch
                + weapon.currentMount().fixedPitch());
    }

    /**
     * World fire direction {yaw, pitch}. With ExperimentalQuaternionRotation the airframe attitude
     * and the barrel angles are composed as quaternions, which stays correct at any roll; otherwise
     * this is the plain sum of {@link #yaw} and {@link #pitch}, which is only exact at zero roll. Only
     * the bullet velocity and the aim displays use it; the muzzle position keeps the plain angles.
     */
    public float[] direction(@Nullable Entity user) {
        WeaponGroup weapon = weaponOf(user);
        if (!this.quaternionAttitude.getAsBoolean() || weapon == null) {
            return new float[] {yaw(user), pitch(user)};
        }
        float relativeYaw = weapon.yaw() + weapon.currentMount().fixedYaw();
        float relativePitch = weapon.pitch() + weapon.currentMount().fixedPitch();
        AircraftEntity v = this.vehicle;
        return Rotations.worldAim(v.getYRot(), v.getXRot(), v.roll(), relativeYaw, relativePitch);
    }

    public float[] direction(@Nullable Entity user, float partialTicks) {
        WeaponGroup weapon = weaponOf(user);
        if (!this.quaternionAttitude.getAsBoolean() || weapon == null) {
            return new float[] {yaw(user, partialTicks), pitch(user, partialTicks)};
        }
        float weaponYaw = weapon.turret().prevYaw() + (weapon.yaw() - weapon.turret().prevYaw()) * partialTicks;
        float weaponPitch = weapon.turret().prevPitch() + (weapon.pitch() - weapon.turret().prevPitch()) * partialTicks;
        float relativeYaw = weaponYaw + weapon.currentMount().fixedYaw();
        float relativePitch = weaponPitch + weapon.currentMount().fixedPitch();
        AircraftEntity v = this.vehicle;
        return Rotations.worldAim(Mth.lerp(partialTicks, v.yRotO, v.getYRot()), Mth.lerp(partialTicks, v.xRotO, v.getXRot()),
                this.roll.interpolated(partialTicks), relativeYaw, relativePitch);
    }

    /** A point on the weapon, given in vehicle coordinates, turned with the weapon into a world offset. */
    public Vec3 muzzleOffset(Vec3 localPos, @Nullable Entity user) {
        AircraftEntity v = this.vehicle;
        VehicleParts.Weapon mount = this.weapons.currentMount(user);
        if (mount != null && mount.turret && v.spec() != null) {
            Vec3 turretPos = v.spec().file().turretPosition();
            Vec3 relative = localPos.subtract(turretPos);
            Vec3 rotatedRelative = Rotations.rotate(relative, v.getYRot() - yaw(user), 0.0F, 0.0F);
            return Rotations.rotate(rotatedRelative.add(turretPos), -v.getYRot(), -v.getXRot(), -v.roll());
        }
        return Rotations.rotate(localPos, -yaw(user), -pitch(user), -v.roll());
    }

    public Vec3 muzzleOffset(Vec3 localPos, @Nullable Entity user, float partialTicks) {
        AircraftEntity v = this.vehicle;
        VehicleParts.Weapon mount = this.weapons.currentMount(user);
        float roll = this.roll.interpolated(partialTicks);
        if (mount != null && mount.turret && v.spec() != null) {
            float yaw = Mth.lerp(partialTicks, v.yRotO, v.getYRot());
            Vec3 turretPos = v.spec().file().turretPosition();
            Vec3 relative = localPos.subtract(turretPos);
            Vec3 rotatedRelative = Rotations.rotate(relative, yaw - yaw(user, partialTicks), 0.0F, 0.0F);
            return Rotations.rotate(rotatedRelative.add(turretPos), -yaw, -Mth.lerp(partialTicks, v.xRotO, v.getXRot()), -roll);
        }
        return Rotations.rotate(localPos, -yaw(user, partialTicks), -pitch(user, partialTicks), -roll);
    }
}
