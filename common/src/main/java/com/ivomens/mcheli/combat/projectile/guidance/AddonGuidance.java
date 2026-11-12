package com.ivomens.mcheli.combat.projectile.guidance;

import com.ivomens.mcheli.api.weapon.GuidanceContext;
import com.ivomens.mcheli.api.weapon.GuidanceLaw;
import com.ivomens.mcheli.api.weapon.SteeringCommand;
import com.ivomens.mcheli.combat.projectile.FlightModel;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.core.guidance.Seeker;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * An addon's guidance law ({@link GuidanceLaw}) flying a projectile within the limits MCHeli's own missiles keep
 * ({@link Homing}): the rigid launch phase, the end of homing, the range to the target, the turn rate, the seeker
 * cone and doppler gate, and the decoys the weapon falls for. Server side only; clients see the steered projectile
 * through the entity sync.
 */
public final class AddonGuidance {

    private AddonGuidance() {
    }

    /**
     * One tick of the law, after the projectile moved.
     *
     * @return whether the projectile's flight changed in a way clients cannot foresee
     */
    public static boolean steer(ProjectileEntity missile, GuidanceLaw law, GuidanceContext context) {
        WeaponSpec info = missile.spec();
        Entity target = missile.target();
        if (target != null && target.isRemoved()) {
            missile.setTarget(null);
            target = null;
        }
        if (target != null && missile.distanceToSqr(target) > Homing.MAX_TARGET_DIST_SQ) {
            missile.discard();
            return false;
        }
        if (missile.age() <= info.missile().lockOn().delay()
                || info.missile().tickEndHoming() > 0 && missile.tickCount > info.missile().tickEndHoming()) {
            return false;
        }
        SteeringCommand command = target != null && Homing.targetDropsDecoys(missile, target)
                ? law.decoyed(context) : law.steer(context);
        if (command == null) {
            throw new IllegalStateException("the guidance law returned no command");
        }
        return apply(missile, info, command);
    }

    private static boolean apply(ProjectileEntity missile, WeaponSpec info, SteeringCommand command) {
        switch (command.kind()) {
            case STEER -> {
                return turnTowards(missile, info, command.point());
            }
            case TRACK -> {
                return track(missile, info, command.point());
            }
            case DROP_TARGET -> missile.setTarget(null);
            case DETONATE -> {
                // not a block hit, so grass or leaves where it is cannot swallow the explosion
                missile.impact(BlockHitResult.miss(missile.position(), Direction.DOWN, missile.blockPosition()));
                missile.discard();
            }
            case SELF_DESTRUCT -> missile.discard();
            default -> {
            }
        }
        return false;
    }

    /** Turns towards a point the seeker tracks, unless the seeker loses it. */
    private static boolean track(ProjectileEntity missile, WeaponSpec info, Vec3 point) {
        Entity target = missile.target();
        Vec3 motion = missile.getDeltaMovement();
        Vector3f toPoint = new Vector3f((float) (point.x - missile.getX()), (float) (point.y - missile.getY()),
                (float) (point.z - missile.getZ()));
        boolean lost = target != null
                ? Homing.losesTarget(missile, info, target, motion, target.getDeltaMovement(), toPoint, false)
                : !inSeekerCone(missile, point);
        if (lost) {
            if (target != null) {
                missile.setTarget(null);
            }
            return false;
        }
        return turnTowards(missile, info, point);
    }

    private static boolean turnTowards(ProjectileEntity missile, WeaponSpec info, Vec3 point) {
        double tx = point.x - missile.getX();
        double ty = point.y - missile.getY();
        double tz = point.z - missile.getZ();
        double distance = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (distance < 1.0E-6) {
            return false;
        }
        Vec3 steered = FlightModel.steer(missile, missile.getDeltaMovement(), tx / distance, ty / distance,
                tz / distance, Double.MAX_VALUE, info.missile().turningFactor());
        missile.setDeltaMovement(steered);
        missile.hasImpulse = true;
        FlightModel.faceMotion(missile);
        return true;
    }

    /** Whether the point is inside the seeker's field of view; everywhere during a top attack's climb. */
    public static boolean inSeekerCone(ProjectileEntity missile, Vec3 point) {
        WeaponSpec info = missile.spec();
        if (info == null || missile.isClimbing()) {
            return true;
        }
        Vec3 motion = missile.getDeltaMovement();
        Vector3f flight = new Vector3f((float) motion.x, (float) motion.y, (float) motion.z);
        Vector3f toPoint = new Vector3f((float) (point.x - missile.getX()), (float) (point.y - missile.getY()),
                (float) (point.z - missile.getZ()));
        return !Seeker.outsideFieldOfView(flight, toPoint, info.missile().maxDegreeOfMissile());
    }

    /** Whether decoys the weapon falls for hide the missile's target (looked at every third tick only). */
    public static boolean isTargetDecoyed(ProjectileEntity missile) {
        Entity target = missile.target();
        return target != null && !target.isRemoved() && Homing.targetDropsDecoys(missile, target);
    }
}
