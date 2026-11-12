package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Anti-tank missile. Fired in top-attack mode it climbs high above the target, then dives onto it with
 * extra damage; otherwise it homes straight in. Active radar missiles without a target search for one.
 * <p>
 * Its proximity fuze compares {@code ProximityFuseDist} with the plain distance to the target, unlike
 * the air-to-air missile's squared one ({@link AirToAirGuidance}).
 */
public final class AntiTankGuidance implements Guidance {

    private static final double TOP_ATTACK_CLIMB_HEIGHT = 100.0;
    private static final int TOP_ATTACK_CLIMB_TICKS = 20;
    private static final int TOP_ATTACK_LEVEL_TICKS = 30;
    private static final int TOP_ATTACK_DIVE_BOOST_TICK = 35;
    private static final float TOP_ATTACK_DAMAGE_BONUS = 1.2F;

    @Override
    public void steer(ProjectileEntity missile) {
        if (missile.level().isClientSide) {
            return;
        }
        WeaponSpec info = missile.spec();
        Entity target = missile.target();
        if (missile.shooter() != null && target != null && !target.isRemoved()) {
            this.guide(missile, info, target);
        } else if (info.missile().radar().activeRadar() && info.missile().scanInterval() > 0
                && missile.tickCount % info.missile().scanInterval() == 0) {
            scan(missile, info);
        }
    }

    private void guide(ProjectileEntity missile, WeaponSpec info, Entity target) {
        double distSq = missile.distanceToSqr(target);
        if (distSq > Homing.MAX_TARGET_DIST_SQ) {
            missile.discard();
            return;
        }
        if (missile.age() <= info.missile().lockOn().delay()) return;
        if (Homing.decoyedByExternalTarget(missile, target)) {
            missile.discard();
            return;
        }
        if (missile.isTopAttack()) {
            topAttack(missile, info, target);
        } else if (info.explosion().proximityFuseDist() >= 0.1F
                && distSq < Math.pow(info.explosion().proximityFuseDist(), 2)) {
            Homing.burstNextTo(missile, target);
        } else {
            Homing.steerTowards(missile, target.getX(), target.getY(), target.getZ(), false);
        }
    }

    /** Climb over the target, level off at the shooter's height, then dive with a stronger warhead. */
    private static void topAttack(ProjectileEntity missile, WeaponSpec info, Entity target) {
        int age = missile.age();
        int start = info.missile().lockOn().delay();
        double shooterY = missile.shooter().getY();
        if (age <= start + TOP_ATTACK_CLIMB_TICKS) {
            missile.setClimbing(true);
            Homing.steerTowards(missile, target.getX(), shooterY + TOP_ATTACK_CLIMB_HEIGHT, target.getZ(), false);
        } else if (age <= start + TOP_ATTACK_LEVEL_TICKS) {
            Homing.steerTowards(missile, target.getX(), shooterY, target.getZ(), false);
        } else {
            if (age == start + TOP_ATTACK_DIVE_BOOST_TICK) {
                missile.warhead().boostForDive(TOP_ATTACK_DAMAGE_BONUS);
            }
            missile.setClimbing(false);
            Homing.steerTowards(missile, target.getX(), target.getY(), target.getZ(), false);
        }
    }

    /** Active radar search: picks the grounded target closest to the flight direction. */
    private static void scan(ProjectileEntity missile, WeaponSpec info) {
        // a missile that may not be decoyed by any chaff never acquires targets on its own
        if (info.missile().lockOn().lockedChaffMax() <= 0) {
            missile.setTarget(null);
            return;
        }
        double maxAngle = Math.toRadians(info.missile().lockOn().maxAngle());
        Vec3 flightDir = missile.getDeltaMovement().normalize();
        List<Entity> candidates = missile.level().getEntities(missile,
                missile.getBoundingBox().inflate(info.missile().lockOn().maxRange()));
        if (candidates.isEmpty()) return;
        Entity closest = null;
        double closestAngle = Double.MAX_VALUE;
        for (Entity entity : candidates) {
            if (isFriendly(missile, entity) || !isGroundTarget(entity, info)) continue;
            double angle = angleTo(missile, entity, flightDir);
            if (angle <= maxAngle && angle < closestAngle) {
                closestAngle = angle;
                closest = entity;
            }
        }
        missile.setTargetUnannounced(closest);
    }

    private static boolean isFriendly(ProjectileEntity missile, Entity target) {
        Entity shooter = missile.shooter();
        if (sameId(target, missile.platform()) || sameId(target, shooter)) return true;
        if (shooter instanceof LivingEntity && target.getControllingPassenger() instanceof Player player) {
            return player.isAlliedTo(shooter);
        }
        if (shooter instanceof LivingEntity living && target instanceof LivingEntity other) {
            return other.isAlliedTo(living);
        }
        return false;
    }

    private static boolean sameId(Entity a, Entity b) {
        return (a != null ? a.getId() : -1) == (b != null ? b.getId() : -1);
    }

    private static boolean isGroundTarget(Entity entity, WeaponSpec info) {
        if (!TargetFilter.isOnGround(entity, info.missile().lockOn().minHeight())) return false;
        if (entity instanceof Vehicle) return true;
        return !info.definition().ridableOnly() && entity instanceof LivingEntity && !entity.isPassenger();
    }

    private static double angleTo(ProjectileEntity missile, Entity target, Vec3 flightDir) {
        Vec3 toTarget = new Vec3(target.getX() - missile.getX(), target.getY() - missile.getY(),
                target.getZ() - missile.getZ()).normalize();
        return Math.acos(Mth.clamp(flightDir.dot(toTarget), -1.0, 1.0));
    }
}
