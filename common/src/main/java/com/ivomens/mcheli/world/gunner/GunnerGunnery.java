package com.ivomens.mcheli.world.gunner;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * How a gunner fights from its seat: it looks for the nearest target its weapon can reach every second,
 * turns towards where the target will be at the seat's traverse speed, and fires once on target.
 */
final class GunnerGunnery {

    /** Ticks between target searches, and the shortest time a found target is kept. */
    private static final int RETARGET_TICKS = 20;
    private static final int KEEP_TARGET_TICKS = 60;

    private final GunnerEntity gunner;
    @Nullable
    private Entity target;
    private int switchTargetCount;
    private double targetPrevPosX;
    private double targetPrevPosY;
    private double targetPrevPosZ;
    /** Set once the weapon ran hot; the gunner holds fire until it has cooled down completely. */
    private boolean waitCooldown;

    GunnerGunnery(GunnerEntity gunner) {
        this.gunner = gunner;
    }

    @Nullable
    Entity target() {
        return this.target;
    }

    /** The target search countdown, once per tick on both sides. */
    void tickCountdown() {
        if (this.switchTargetCount > 0) {
            this.switchTargetCount--;
        }
    }

    /** Server: searches, aims at and shoots the target from the host vehicle's current weapon. */
    void fightFrom(AircraftEntity ac) {
        if (ac.isDestroyed() || !ac.seats().gunnersAllowed()) {
            return;
        }
        WeaponGroup weaponSet = ac.weapons().current(this.gunner);
        if (weaponSet == null || weaponSet.spec() == null || weaponSet.currentMount() == null) {
            return;
        }

        // a dead target is dropped at the next regular search at the latest
        if (this.target != null && (!this.target.isAlive()
                || this.target instanceof LivingEntity living && living.getHealth() <= 0.0F)
                && this.switchTargetCount > RETARGET_TICKS) {
            this.switchTargetCount = RETARGET_TICKS;
        }

        Vec3 muzzle = weaponPosition(ac, weaponSet);
        if (this.switchTargetCount <= 0) {
            this.switchTargetCount = RETARGET_TICKS;
            selectTarget(ac, weaponSet, muzzle);
        }

        if (this.target != null) {
            aimAndFire(ac, weaponSet, muzzle);
        } else {
            this.gunner.setXRot(this.gunner.getXRot() * 0.95F);
        }
    }

    private boolean canAttack(LivingEntity entity, AircraftEntity ac, WeaponGroup ws) {
        GunnerEntity gunner = this.gunner;
        boolean attackable;
        if (gunner.targetType() == GunnerEntity.TARGET_MONSTERS) {
            attackable = entity != gunner && !(entity instanceof EnderMan) && entity.isAlive()
                    && !gunner.isAlliedTo(entity) && entity.getHealth() > 0.0F && !ac.seats().isAboard(entity);
        } else {
            // only gunners that belong to a team fight players
            attackable = entity != gunner && !((Player) entity).getAbilities().instabuild && entity.isAlive()
                    && !gunner.getTeamName().isEmpty() && !gunner.isAlliedTo(entity) && entity.getHealth() > 0.0F
                    && !ac.seats().isAboard(entity);
        }

        if (attackable && ws.currentMount().seeker() != null) {
            attackable = ws.currentMount().seeker().canLock(entity, null);
        }
        return attackable;
    }

    private void selectTarget(AircraftEntity ac, WeaponGroup weaponSet, Vec3 muzzle) {
        GunnerEntity gunner = this.gunner;
        List<? extends LivingEntity> candidates;
        if (gunner.targetType() == GunnerEntity.TARGET_MONSTERS) {
            int horizontal = McheliConfig.combat().gunnerVsMonster().horizontal();
            int vertical = McheliConfig.combat().gunnerVsMonster().vertical();
            candidates = gunner.level().getEntitiesOfClass(LivingEntity.class,
                    gunner.getBoundingBox().inflate(horizontal, vertical, horizontal),
                    entity -> entity instanceof Enemy);
        } else {
            int horizontal = McheliConfig.combat().gunnerVsPlayer().horizontal();
            int vertical = McheliConfig.combat().gunnerVsPlayer().vertical();
            candidates = gunner.level().getEntitiesOfClass(Player.class,
                    gunner.getBoundingBox().inflate(horizontal, vertical, horizontal));
        }

        LivingEntity nearest = null;
        for (LivingEntity entity : candidates) {
            if (canAttack(entity, ac, weaponSet) && isWithinPitch(entity, ac, muzzle)
                    && (nearest == null || gunner.distanceTo(entity) < gunner.distanceTo(nearest))
                    && gunner.hasLineOfSight(entity) && isWithinYaw(entity, ac, muzzle)) {
                nearest = entity;
                this.switchTargetCount = KEEP_TARGET_TICKS;
            }
        }

        if (nearest != null && this.target != nearest) {
            this.targetPrevPosX = nearest.getX();
            this.targetPrevPosY = nearest.getY();
            this.targetPrevPosZ = nearest.getZ();
        }
        this.target = nearest;
    }

    /** Turns towards the target's predicted position at the seat's traverse speed and fires once on target. */
    private void aimAndFire(AircraftEntity ac, WeaponGroup weaponSet, Vec3 muzzle) {
        GunnerEntity gunner = this.gunner;
        Entity target = this.target;
        WeaponMount weapon = weaponSet.currentMount();
        float turnSpeed = ac.isPilot(gunner) ? ac.spec().file().camera().rotationSpeed() / 10.0F : 10.0F;
        float pitch = Mth.wrapDegrees(gunner.getXRot());
        float yaw = Mth.wrapDegrees(gunner.getYRot());

        double leadTicks = this.leadTicks(weaponSet);
        double leadX = (target.getX() - this.targetPrevPosX) * leadTicks;
        double leadY = (target.getY() - this.targetPrevPosY) * leadTicks
                + target.getBbHeight() * gunner.getRandom().nextDouble();
        double leadZ = (target.getZ() - this.targetPrevPosZ) * leadTicks;
        double dx = target.getX() + leadX - muzzle.x;
        double dy = target.getY() + leadY - muzzle.y;
        double dz = target.getZ() + leadZ - muzzle.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float aimYaw = Mth.wrapDegrees((float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F);
        float aimPitch = (float) (-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));

        if (Math.abs(pitch - aimPitch) < turnSpeed && Math.abs(yaw - aimYaw) < turnSpeed) {
            float spread = ac.isPilot(gunner) ? 0.1F : 0.5F;
            pitch = aimPitch + (gunner.getRandom().nextFloat() - 0.5F) * spread - weapon.fixedPitch();
            yaw = aimYaw + (gunner.getRandom().nextFloat() - 0.5F) * spread;
            gunner.setXRot(pitch);
            gunner.setYRot(yaw);
            fire(ac, weaponSet, weapon);
        }

        if (Math.abs(aimPitch - pitch) >= turnSpeed) {
            pitch += aimPitch > pitch ? turnSpeed : -turnSpeed;
        }
        if (Math.abs(aimYaw - yaw) >= turnSpeed) {
            // turn the short way round
            if (Math.abs(aimYaw - yaw) <= 180.0F) {
                yaw += aimYaw > yaw ? turnSpeed : -turnSpeed;
            } else {
                yaw += aimYaw > yaw ? -turnSpeed : turnSpeed;
            }
        }

        gunner.setXRot(pitch);
        gunner.setYRot(yaw);
        gunner.yHeadRot = yaw;
        this.targetPrevPosX = target.getX();
        this.targetPrevPosY = target.getY();
        this.targetPrevPosZ = target.getZ();
    }

    /** How far ahead to lead the target: the projectile's flight time to it. */
    private double leadTicks(WeaponGroup weaponSet) {
        double leadTicks = 1.0;
        double distance = this.gunner.distanceTo(this.target);
        if (distance >= 10.0 && weaponSet.spec().ballistics().acceleration() > 1.0F) {
            leadTicks = distance / weaponSet.spec().ballistics().acceleration();
        }
        Entity targetVehicle = this.target.getVehicle();
        if (targetVehicle instanceof SeatEntity || targetVehicle instanceof AircraftEntity) {
            leadTicks -= McheliConfig.debug().hitBoxDelayTick();
        }
        return leadTicks;
    }

    private void fire(AircraftEntity ac, WeaponGroup weaponSet, WeaponMount weapon) {
        int maxHeat = weaponSet.spec().missile().heat().maxHeatCount();
        if (this.waitCooldown && weaponSet.heat() > 0 && maxHeat > 0) {
            return;
        }

        this.waitCooldown = false;
        FireRequest request = weapon.seeker() != null ? FireRequest.locked(this.target.getId(), 0) : FireRequest.NONE;
        // let the barrel cool down fully once it gets close to overheating
        if (ac.weapons().fire(this.gunner, request) && maxHeat > 0 && weaponSet.heat() > maxHeat * 4 / 5) {
            this.waitCooldown = true;
        }
    }

    /** Whether the target is within the seat's (or weapon's) pitch limits. */
    private boolean isWithinPitch(LivingEntity entity, AircraftEntity ac, Vec3 pos) {
        // weapons or seats missing from the definition count as unlimited
        try {
            double dx = entity.getX() - pos.x;
            double dy = entity.getY() - pos.y;
            double dz = entity.getZ() - pos.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));
            VehicleSpec ai = ac.spec();
            if (ac instanceof GroundVehicleEntity && ac.isPilot(this.gunner)
                    && Math.abs(ai.file().physics().rotationLimits().minPitch())
                            + Math.abs(ai.file().physics().rotationLimits().maxPitch()) > 0.0F
                    && (pitch < ai.file().physics().rotationLimits().minPitch()
                            || pitch > ai.file().physics().rotationLimits().maxPitch())) {
                return false;
            }

            WeaponMount cw = ac.weapons().current(this.gunner).currentMount();
            if (cw.seeker() == null) {
                VehicleParts.Weapon wi = ai.armament().getWeaponById(ac.weapons().currentId(this.gunner));
                boolean limited = Math.abs(wi.minPitch) + Math.abs(wi.maxPitch) > 0.0F;
                if (limited && (pitch < wi.minPitch || pitch > wi.maxPitch)) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }

        return true;
    }

    /** Where the gunner's weapon fires from. */
    private Vec3 weaponPosition(AircraftEntity ac, WeaponGroup ws) {
        SeatInfo seatInfo = ac.seats().info(this.gunner);
        return (seatInfo == null || !seatInfo.rotSeat) && !(ac instanceof GroundVehicleEntity)
                ? ac.body().toWorld(ws.currentMount().position())
                : ac.seats().turretOffset(ws.currentMount().position()).add(ac.getX(), ac.getY(), ac.getZ());
    }

    /** Whether the target is within the yaw range of a weapon without seeker on an aircraft. */
    private boolean isWithinYaw(LivingEntity entity, AircraftEntity ac, Vec3 pos) {
        if (ac instanceof GroundVehicleEntity) {
            return true;
        }
        try {
            if (ac.weapons().current(this.gunner).currentMount().seeker() != null) {
                return true;
            }
            VehicleParts.Weapon wi = ac.spec().armament().getWeaponById(ac.weapons().currentId(this.gunner));
            Vec3 v1 = new Vec3(0.0, 0.0, 1.0);
            float yaw = -ac.getYRot() + (wi.maxYaw + wi.minYaw) / 2.0F - wi.defaultYaw;
            v1 = v1.yRot(yaw * (float) Math.PI / 180.0F);
            Vec3 v2 = new Vec3(entity.getX() - pos.x, 0.0, entity.getZ() - pos.z).normalize();
            double deg = Math.acos(v1.dot(v2)) * 180.0 / Math.PI;
            return deg < Math.abs(wi.maxYaw - wi.minYaw) / 2.0F;
        } catch (Exception e) {
            return false;
        }
    }
}
