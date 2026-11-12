package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.damage.ModDamageSources;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;

/**
 * The fuzes a weapon definition can give its projectile, and their state:
 * <ul>
 *   <li>delay fuze: started on impact (the projectile bounces) or in an unloaded chunk;</li>
 *   <li>time fuze, range limit and altitude fuze, checked by the server each tick;</li>
 *   <li>airburst fuze: goes off once the projectile has flown the distance the shooter set;</li>
 *   <li>proximity fuze: goes off next to an aircraft or the missile it is locked on to.</li>
 * </ul>
 * The proximity fuze of homing missiles is a different thing and part of their guidance.
 */
public final class Fuzes {

    /** Projectiles further than this (horizontally, squared) from their shooter are removed. */
    private static final double MAX_SHOOTER_DIST_SQ = 3.38724E7;
    /** Ticks between refreshes of the proximity fuze candidate list. */
    private static final int PROXIMITY_CANDIDATE_REFRESH = 4;
    /** Airburst distances outside (MIN, MAX) mean the airburst fuze is off. */
    private static final int AIRBURST_MIN_DIST = 5;
    private static final int AIRBURST_MAX_DIST = 3000;
    /** The airburst fuze goes off this much further than the distance set. */
    private static final double AIRBURST_EXTRA = 3.0;

    private final ProjectileEntity projectile;
    private int delayTicks;
    private int airburstDistance;
    private double airburstTravelled;
    private boolean airburstTriggered;
    /** An AHEAD round past its airburst point streams sub-projectiles instead of exploding. */
    private boolean aheadArmed;
    private List<Entity> proximityCandidates = Collections.emptyList();
    private int proximityCandidatesTick = Integer.MIN_VALUE;

    Fuzes(ProjectileEntity projectile) {
        this.projectile = projectile;
    }

    public int airburstDistance() {
        return this.airburstDistance;
    }

    public void setAirburstDistance(int distance) {
        this.airburstDistance = distance;
    }

    boolean isAheadArmed() {
        return this.aheadArmed;
    }

    private static boolean isAirburstSet(int distance) {
        return distance > AIRBURST_MIN_DIST && distance < AIRBURST_MAX_DIST;
    }

    /** Starts the delay fuze, unless it already runs. */
    void startDelay(WeaponSpec info) {
        if (this.delayTicks == 0) {
            this.delayTicks = info.explosion().fuseDelay();
        }
    }

    /**
     * Server: unloaded chunks, the delay and time fuzes, the range limit and the altitude fuze.
     *
     * @return false if the projectile is gone
     */
    boolean checkServer(WeaponSpec info) {
        ProjectileEntity p = this.projectile;
        BlockPos blockPos = p.blockPosition();
        if (!p.level().hasChunkAt(blockPos)) {
            if (info.explosion().fuseDelay() <= 0) {
                p.discard();
                return false;
            }
            this.startDelay(info);
        }
        if (this.delayTicks > 0 && --this.delayTicks == 0) {
            p.warhead().detonateOnTimeout(p);
            p.discard();
            return false;
        }
        boolean timedOut = info.explosion().fuseTime() > 0 && p.age() > info.explosion().fuseTime();
        if (!isInRange(p) || timedOut) {
            if (info.explosion().fuseTime() > 0) {
                p.warhead().detonateOnTimeout(p);
            }
            p.discard();
            return false;
        }
        if (info.explosion().altitude() > 0 && Terrain.hasBlockY(p, 3, -info.explosion().altitude())) {
            p.impact(new BlockHitResult(p.position(), Direction.DOWN, blockPos, false));
            return !p.isRemoved();
        }
        return true;
    }

    /** False once the shooter is gone, or the projectile is very far away or below the world. */
    private static boolean isInRange(ProjectileEntity p) {
        Entity owner = p.shooter() != null ? p.shooter() : p.platform();
        if (owner == null) {
            return false;
        }
        double x = p.getX() - owner.getX();
        double z = p.getZ() - owner.getZ();
        return x * x + z * z < MAX_SHOOTER_DIST_SQ && p.getY() > p.level().getMinBuildHeight() - 10.0;
    }

    /**
     * Airburst fuze: goes off once the projectile has flown the set distance. AHEAD rounds do not go
     * off but arm, and then stream sub-projectiles forward (see {@link Submunition#spread}).
     */
    void checkAirburst(WeaponSpec info) {
        if (this.airburstTriggered || !isAirburstSet(this.airburstDistance)) return;
        ProjectileEntity p = this.projectile;
        double triggerDistance = this.airburstDistance + AIRBURST_EXTRA;
        Vec3 step = p.getDeltaMovement().scale(p.stepFactor());
        double stepLength = step.length();
        double travelledBefore = this.airburstTravelled;
        double travelled = travelledBefore + stepLength;
        if (stepLength <= 0.0 || travelled < triggerDistance) {
            this.airburstTravelled = travelled;
            return;
        }
        this.airburstTriggered = true;
        this.airburstTravelled = 0.0;
        if (p.level().isClientSide) {
            return;
        }
        if (info.submunition().ahead()) {
            // the proximity fuze tick keeps AHEAD rounds from arming too early
            if (info.missile().lockOn().proximityFuseTick() < 0
                    || p.tickCount > info.missile().lockOn().proximityFuseTick()) {
                this.aheadArmed = true;
            }
            return;
        }
        double t = (triggerDistance - travelledBefore) / stepLength;
        p.warhead().detonateInAir(p, p.getX() + step.x * t, p.getY() + step.y * t, p.getZ() + step.z * t, info);
        p.discard();
    }

    /**
     * Proximity fuze: goes off near aircraft (and a locked missile target), predicting where the target
     * will be when this tick's path passes it. AHEAD rounds only use it once an airburst distance is set.
     * <p>
     * The candidates are refreshed every few ticks with a box covering the flight of those ticks,
     * instead of an entity query every tick.
     */
    void checkProximity(WeaponSpec info) {
        ProjectileEntity p = this.projectile;
        if (info.missile().lockOn().proximityFuseTick() < 0
                || p.tickCount <= info.missile().lockOn().proximityFuseTick()) return;
        if (info.explosion().proximityFuseDist() <= 0.0F) return;
        if (info.submunition().ahead() && !isAirburstSet(this.airburstDistance)) return;
        Vec3 step = p.getDeltaMovement().scale(p.stepFactor());
        double stepLength = step.length();
        if (stepLength <= 0.0) return;
        this.refreshCandidates(info, step);
        for (Entity entity : this.proximityCandidates) {
            if (entity.isRemoved() || !Collisions.canHit(p, entity)) continue;
            boolean isAircraft = entity instanceof Vehicle;
            boolean isLockedTarget = entity instanceof ProjectileEntity && p.target() != null
                    && !p.target().isRemoved() && entity.getId() == p.target().getId();
            if (!isAircraft && !isLockedTarget) continue;
            if (isAircraft && TargetFilter.isOnGround(entity, info.missile().lockOn().proximityFuseHeight())) continue;
            double t = interceptFraction(p, entity, step, stepLength, info.explosion().proximityFuseDist());
            if (t < 0.0) continue;
            if (!p.level().isClientSide) {
                p.warhead().detonateNear(p, entity, isLockedTarget,
                        new Vec3(p.getX() + step.x * t, p.getY() + step.y * t, p.getZ() + step.z * t), info);
            }
            return;
        }
    }

    private void refreshCandidates(WeaponSpec info, Vec3 step) {
        ProjectileEntity p = this.projectile;
        if (p.tickCount - this.proximityCandidatesTick < PROXIMITY_CANDIDATE_REFRESH) {
            return;
        }
        this.proximityCandidatesTick = p.tickCount;
        float searchRange = info.explosion().proximityFuseDist() * 5.0F;
        AABB box = p.getBoundingBox().expandTowards(step.x * PROXIMITY_CANDIDATE_REFRESH,
                step.y * PROXIMITY_CANDIDATE_REFRESH, step.z * PROXIMITY_CANDIDATE_REFRESH).inflate(searchRange);
        this.proximityCandidates = p.level().getEntities(p, box,
                e -> e instanceof Vehicle || e instanceof ProjectileEntity);
    }

    /**
     * Where along this tick's step (0..1) the projectile passes within {@code fuseDist} of the entity's
     * predicted position, or -1 if it does not.
     */
    private static double interceptFraction(ProjectileEntity p, Entity entity, Vec3 step, double stepLength,
                                            double fuseDist) {
        double dirX = step.x / stepLength;
        double dirY = step.y / stepLength;
        double dirZ = step.z / stepLength;
        // lead the target by its velocity over the rough time to intercept
        double timeToTarget = Math.sqrt(p.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())) / stepLength;
        Vec3 em = entity.getDeltaMovement();
        double toTargetX = entity.getX() + em.x * timeToTarget - p.getX();
        double toTargetY = entity.getY() + em.y * timeToTarget - p.getY();
        double toTargetZ = entity.getZ() + em.z * timeToTarget - p.getZ();
        double along = toTargetX * dirX + toTargetY * dirY + toTargetZ * dirZ;
        if (along <= 0.0) return -1.0;
        double perpX = toTargetX - dirX * along;
        double perpY = toTargetY - dirY * along;
        double perpZ = toTargetZ - dirZ * along;
        if (Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ) > fuseDist) return -1.0;
        // the projection lies on the unit direction, so its distance from here equals "along"
        if (along > stepLength + 0.1) return -1.0;
        return Math.min(1.0, along / stepLength);
    }

    /**
     * Whether a hit sets the projectile off: one by a thrown item (a snowball, a grenade...) on the server, unless
     * it is a marker rocket.
     */
    static boolean setsOff(ProjectileEntity p, DamageSource source, float amount) {
        return p.kind().role() != ProjectileKind.Role.MARKER && !p.isInvulnerableTo(source)
                && !p.level().isClientSide && amount > 0.0F && ModDamageSources.isThrown(source);
    }
}
