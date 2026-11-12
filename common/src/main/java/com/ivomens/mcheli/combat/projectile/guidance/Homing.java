package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.combat.countermeasure.CountermeasureFlag;
import com.ivomens.mcheli.api.target.TargetSignature;
import com.ivomens.mcheli.combat.guidance.ExternalTargets;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.combat.projectile.FlightModel;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.core.guidance.Pursuit;
import com.ivomens.mcheli.core.guidance.Seeker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Homing on a target entity, shared by the missiles that chase one. Each tick the missile turns
 * towards the target's position (optionally led by its velocity), and drops the lock when the target
 * leaves the seeker's field of view, moves outside the doppler gate or, for air-to-air missiles, lands.
 */
final class Homing {

    /** Missiles further than this from their target (1850 blocks, squared) are removed. */
    static final double MAX_TARGET_DIST_SQ = 3422500.0;
    /** Flares and chaff are only looked for every few ticks. */
    private static final int DECOY_CHECK_INTERVAL = 3;
    private static final double DECOY_RANGE = 15.0;

    private Homing() {
    }

    /**
     * Turns the missile towards the point.
     *
     * @param landedTargetIsLost the target counts as lost once it is on the ground (air-to-air missiles)
     */
    static void steerTowards(ProjectileEntity missile, double targetX, double targetY, double targetZ,
                             boolean landedTargetIsLost) {
        WeaponSpec info = missile.spec();
        Entity target = missile.target();
        if (info == null) return;
        if (info.missile().tickEndHoming() > 0 && missile.tickCount > info.missile().tickEndHoming()) return;
        if (target == null || target.isRemoved()) return;

        Vec3 motion = missile.getDeltaMovement();
        Vec3 targetMotion = target.getDeltaMovement();
        if (info.missile().predictTargetPos()) {
            Vector3d led = Pursuit.lead(new Vector3d(missile.getX(), missile.getY(), missile.getZ()),
                    new Vector3d(motion.x, motion.y, motion.z), missile.speed(),
                    new Vector3d(targetX, targetY, targetZ),
                    new Vector3d(targetMotion.x, targetMotion.y, targetMotion.z));
            targetX = led.x;
            targetY = led.y;
            targetZ = led.z;
        }
        double tx = targetX - missile.getX();
        double ty = targetY - missile.getY();
        double tz = targetZ - missile.getZ();
        double distance = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (distance < 1.0E-6) return;

        if (losesTarget(missile, info, target, motion, targetMotion, new Vector3f((float) tx, (float) ty, (float) tz),
                landedTargetIsLost)) {
            missile.setTarget(null);
            return;
        }
        Vec3 steered = FlightModel.steer(missile, motion, tx / distance, ty / distance, tz / distance, Double.MAX_VALUE,
                info.missile().turningFactor());
        missile.setDeltaMovement(steered);
        missile.hasImpulse = true;
        missile.setYRot(Pursuit.headingYaw(steered.x, steered.z));
    }

    static boolean losesTarget(ProjectileEntity missile, WeaponSpec info, Entity target, Vec3 motion,
                                       Vec3 targetMotion, Vector3f toTarget, boolean landedTargetIsLost) {
        Vector3f missileDir = new Vector3f((float) motion.x, (float) motion.y, (float) motion.z);
        if (Seeker.outsideFieldOfView(missileDir, toTarget, info.missile().maxDegreeOfMissile())
                && !missile.isClimbing()) {
            return true;
        }
        Vector3f targetDir = new Vector3f((float) targetMotion.x, (float) targetMotion.y, (float) targetMotion.z);
        if (Seeker.outsideDopplerGate(missileDir, targetDir, info.missile().pdhdnMaxDegree())) {
            return true;
        }
        return landedTargetIsLost && TargetFilter.isOnGround(target, info.missile().lockOn().minHeight());
    }

    /**
     * Whether something near the target is dropping what decoys the missile (looked at every third tick only):
     * flares for a heat seeker, chaff for a radar missile.
     */
    static boolean targetDropsDecoys(ProjectileEntity missile, Entity target) {
        if (missile.age() % DECOY_CHECK_INTERVAL != 0) {
            return false;
        }
        boolean flares = TargetFilter.decoyedByFlares(missile.spec());
        boolean chaff = TargetFilter.decoyedByChaff(missile.spec());
        return !missile.level().getEntities(missile, target.getBoundingBox().inflate(DECOY_RANGE),
                entity -> flares && CountermeasureFlag.FLARE_USING.isSet(entity)
                        || chaff && CountermeasureFlag.CHAFF_USING.isSet(entity)).isEmpty();
    }

    /**
     * Whether the target is an addon's entity whose signature says it drops what decoys the missile, which
     * destroys the missile as a vehicle's flares or chaff do.
     */
    static boolean decoyedByExternalTarget(ProjectileEntity missile, Entity target) {
        ExternalTargets external = ExternalTargets.get();
        if (!external.isActive()) {
            return false;
        }
        TargetSignature signature = external.signature(target);
        if (signature == null || !signature.isFlareUsing() && !signature.isChaffUsing()) {
            return false;
        }
        ThreatSeeker seeker = ThreatSeeker.of(missile.spec());
        return signature.isFlareUsing() && seeker.decoyedByFlares()
                || signature.isChaffUsing() && seeker.decoyedByChaff();
    }

    /** Sets the missile off between itself and the target, as a hit on the target. */
    static void burstNextTo(ProjectileEntity missile, Entity target) {
        missile.setPos((target.getX() + missile.getX()) / 2.0, (target.getY() + missile.getY()) / 2.0,
                (target.getZ() + missile.getZ()) / 2.0);
        missile.impact(new EntityHitResult(target, target.position()));
    }
}
