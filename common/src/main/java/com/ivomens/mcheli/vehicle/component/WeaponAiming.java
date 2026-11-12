package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.net.AirburstDistanceMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Turns each weapon towards its operator's aim, limited by the mount's traverse and turn speed.
 * Unmanned weapons follow the last rider's heading as a turret.
 */
public final class WeaponAiming {

    private final AircraftEntity vehicle;
    private final WeaponComponent weapons;
    private final CrewAimComponent aim;
    private final TowComponent tow;

    WeaponAiming(AircraftEntity vehicle, WeaponComponent weapons, CrewAimComponent aim, TowComponent tow) {
        this.vehicle = vehicle;
        this.weapons = weapons;
        this.aim = aim;
        this.tow = tow;
    }

    /** Degrees per second the weapon's barrel (and the gunner's camera) may turn. */
    public float rotationSpeed(@Nullable WeaponGroup weapon) {
        VehicleSpec info = this.vehicle.spec();
        if (info == null) {
            return 0.0F;
        }
        float speed = Math.max(0.0F, info.file().camera().rotationSpeed());
        if (weapon != null && weapon.spec() != null) {
            speed *= Math.max(0.0F, weapon.spec().camera().rotationSpeedPitch());
        }
        return speed;
    }

    /**
     * True when this rider's current weapon aims independently of the airframe. That needs both a
     * weapon with its own traverse and a vehicle that supports detached aim: a helicopter pilot
     * carrying a pitch-limited rocket pod still flies with the mouse, so the aim follows the
     * airframe, not the other way round.
     */
    public boolean hasIndependentAim(@Nullable Entity user) {
        if (!this.aim.supportsDetachedTurret()) {
            return false;
        }
        VehicleParts.Weapon mount = this.weapons.currentMount(user);
        return mount != null && hasIndependentAim(mount);
    }

    /** Whether any of the type's weapon mounts aims on its own; false while the type is unknown. */
    public static boolean hasAnyIndependentMount(@Nullable VehicleSpec info) {
        if (info == null) {
            return false;
        }
        for (VehicleParts.WeaponSet weaponSet : info.armament().weaponSets()) {
            for (VehicleParts.Weapon mount : weaponSet.weapons) {
                if (hasIndependentAim(mount)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sets the airburst distance of a weapon to the block the player looks at, or clears it when it
     * was already set. The server only traces as far as chunks are loaded around players.
     */
    public void resetAirburstDistance(Player player, WeaponMount weapon) {
        AircraftEntity vehicle = this.vehicle;
        if (weapon == null) {
            return;
        }
        int distance = 0;
        if (weapon.airburstDistance() <= 0) {
            Vec3 start = player.getEyePosition(1.0F);
            double range = 3000.0;
            if (vehicle.level() instanceof ServerLevel serverLevel) {
                range = Math.min(range, serverLevel.getServer().getPlayerList().getViewDistance() * 16.0);
            }
            Vec3 end = start.add(player.getViewVector(1.0F).scale(range));
            BlockHitResult hit = vehicle.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                distance = (int) Math.sqrt(player.distanceToSqr(hit.getLocation()));
            }
        }
        weapon.setAirburstDistance(distance);
        if (vehicle.level().isClientSide) {
            Network.sendToServer(new AirburstDistanceMessage(vehicle.getId(), distance));
        }
    }

    /**
     * Turns every weapon towards its operator's aim right away, before a shot is fired, so the
     * shot leaves where the operator is looking rather than where the barrel was last tick.
     */
    public void updateRotations() {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null || info.armament().getWeaponCount() <= 0 || ac.isDestroyed()) {
            return;
        }
        float yaw = Mth.wrapDegrees(ac.getYRot());
        float pitch = Mth.wrapDegrees(ac.getXRot());
        WeaponGroup[] weapons = this.weapons.all();
        for (int weaponId = 0; weaponId < weapons.length; weaponId++) {
            WeaponGroup weapon = weapons[weaponId];
            VehicleParts.Weapon mount = info.armament().getWeaponById(weaponId);
            if (mount != null) {
                aim(weapon, mount, weaponId, yaw, pitch, false);
            }
            weapon.turret().setPrevYaw(weapon.yaw());
        }
    }

    /**
     * Steps a weapon towards its operator's aim, limited by the mount's traverse and turn speed.
     * Unmanned weapons follow the last rider's heading as a turret.
     */
    void aim(WeaponGroup weapon, VehicleParts.Weapon mount, int weaponId, float yaw, float pitch,
                           boolean centreWhenCarried) {
        AircraftEntity ac = this.vehicle;
        Entity operator = this.weapons.operator(weaponId, mount);
        if (!WeaponComponent.isOperator(operator)) {
            weapon.turret().setTurretYaw(this.aim.riderYaw() - ac.getYRot());
            if (centreWhenCarried && (this.tow.towedByChain() != null || ac.getVehicle() != null)) {
                weapon.turret().setYaw(0.0F);
            }
            return;
        }

        float turnStep = rotationSpeed(weapon) / 20.0F;
        if (hasYawControl(mount)) {
            float turretYaw = mount.turret ? Mth.wrapDegrees(this.aim.riderYaw()) - yaw : 0.0F;
            float aimYaw = Mth.wrapDegrees(this.aim.userYaw(operator) - yaw - mount.defaultYaw - turretYaw);
            if (Math.abs((int) mount.minYaw) < 360 && Math.abs((int) mount.maxYaw) < 360) {
                float targetYaw = LegacyMath.limit(aimYaw, mount.minYaw, mount.maxYaw);
                float weaponYaw = stepWrappedAngle(weapon.yaw() - mount.defaultYaw - turretYaw, targetYaw, turnStep);
                weapon.turret().setYaw(weaponYaw + mount.defaultYaw + turretYaw);
            } else {
                weapon.turret().setYaw(stepWrappedAngle(weapon.yaw(), aimYaw + turretYaw, turnStep));
            }
        }

        float aimPitch = Mth.wrapDegrees(this.aim.userPitch(operator) - pitch);
        float targetPitch = LegacyMath.limit(aimPitch, getMinPitch(mount), getMaxPitch(mount));
        weapon.turret().setPitch(weapon.pitch() + Mth.clamp(targetPitch - weapon.pitch(), -turnStep, turnStep));
        weapon.turret().setTurretYaw(0.0F);
    }

    public static boolean hasYawControl(VehicleParts.Weapon mount) {
        return mount.turret || Math.abs(mount.minYaw) > 0.001F || Math.abs(mount.maxYaw) > 0.001F;
    }

    public static boolean hasIndependentAim(VehicleParts.Weapon mount) {
        return hasYawControl(mount) || hasExplicitPitchLimit(mount);
    }

    private static float stepWrappedAngle(float current, float target, float maxStep) {
        return Mth.wrapDegrees(current + Mth.clamp(Mth.wrapDegrees(target - current), -maxStep, maxStep));
    }

    /** Turrets without their own pitch limits use the vehicle's rotation limits. */
    private float getMinPitch(VehicleParts.Weapon mount) {
        VehicleSpec info = this.vehicle.spec();
        return mount.turret && !hasExplicitPitchLimit(mount) && info != null
                ? info.file().physics().rotationLimits().minPitch() : mount.minPitch;
    }

    private float getMaxPitch(VehicleParts.Weapon mount) {
        VehicleSpec info = this.vehicle.spec();
        return mount.turret && !hasExplicitPitchLimit(mount) && info != null
                ? info.file().physics().rotationLimits().maxPitch() : mount.maxPitch;
    }

    private static boolean hasExplicitPitchLimit(VehicleParts.Weapon mount) {
        return Math.abs(mount.minPitch) > 0.001F || Math.abs(mount.maxPitch) > 0.001F;
    }
}
