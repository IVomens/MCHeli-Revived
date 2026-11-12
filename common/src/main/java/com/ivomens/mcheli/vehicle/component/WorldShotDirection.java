package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Ground vehicle turrets aim in world space: the gunner's view is the barrel's direction, not an
 * angle added to the hull. The quaternion composition of hull-relative barrels does not apply, so
 * the plain angles are used whatever ExperimentalQuaternionRotation says.
 */
final class WorldShotDirection extends ShotDirection {

    WorldShotDirection(AircraftEntity vehicle, WeaponComponent weapons, CrewAimComponent aim, RollComponent roll) {
        super(vehicle, weapons, aim, roll, () -> false);
    }

    @Override
    public float yaw(@Nullable Entity user) {
        WeaponGroup weapon = user != null ? this.weapons.current(user) : null;
        if (weapon != null) {
            return Mth.wrapDegrees(this.aim.userYaw(user) + weapon.currentMount().fixedYaw());
        }
        return super.yaw(user);
    }

    @Override
    public float pitch(@Nullable Entity user) {
        WeaponGroup weapon = user != null ? this.weapons.current(user) : null;
        if (weapon != null) {
            return Mth.wrapDegrees(this.aim.userPitch(user) + weapon.currentMount().fixedPitch());
        }
        return super.pitch(user);
    }

    @Override
    public float[] direction(@Nullable Entity user) {
        return new float[] {yaw(user), pitch(user)};
    }

    @Override
    public float[] direction(@Nullable Entity user, float partialTicks) {
        return new float[] {yaw(user, partialTicks), pitch(user, partialTicks)};
    }

    @Override
    public Vec3 muzzleOffset(Vec3 localPos, @Nullable Entity user) {
        CrewAimComponent aim = this.aim;
        return Rotations.rotate(localPos, -aim.userYaw(user), -aim.userPitch(user), -this.vehicle.roll());
    }

    @Override
    public Vec3 muzzleOffset(Vec3 localPos, @Nullable Entity user, float partialTicks) {
        CrewAimComponent aim = this.aim;
        return Rotations.rotate(localPos, -aim.userYaw(user, partialTicks), -aim.userPitch(user, partialTicks),
                -this.roll.interpolated(partialTicks));
    }
}
