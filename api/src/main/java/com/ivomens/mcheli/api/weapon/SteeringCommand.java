package com.ivomens.mcheli.api.weapon;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * What a {@link GuidanceLaw} tells its projectile to do for one tick. Make one with the factories; the commands
 * without a point are shared constants, so returning them allocates nothing. Usable from any thread.
 *
 * <p><b>Stability.</b> Since API 1.1. A minor version may add factories and {@link Kind} constants.
 *
 * @param kind  what the command does
 * @param point the point to steer towards or track; null for every other kind
 */
public record SteeringCommand(Kind kind, @Nullable Vec3 point) {

    /** What a command does. A {@code switch} over it needs a {@code default} branch. */
    public enum Kind {
        /** Fly on as now. */
        KEEP_COURSE,
        /** Turn towards a point, limited by the turn rate; no seeker involved (command guidance, beam riding). */
        STEER,
        /**
         * Turn towards a point the seeker tracks: limited by the turn rate, and the lock breaks (target dropped,
         * course kept) when the point leaves the seeker cone or the target the doppler gate.
         */
        TRACK,
        /** Drop the target and fly on. */
        DROP_TARGET,
        /** Set the warhead off where the projectile is, as a fuze would. */
        DETONATE,
        /** Remove the projectile without exploding. */
        SELF_DESTRUCT
    }

    private static final SteeringCommand KEEP_COURSE = new SteeringCommand(Kind.KEEP_COURSE, null);
    private static final SteeringCommand DROP_TARGET = new SteeringCommand(Kind.DROP_TARGET, null);
    private static final SteeringCommand DETONATE = new SteeringCommand(Kind.DETONATE, null);
    private static final SteeringCommand SELF_DESTRUCT = new SteeringCommand(Kind.SELF_DESTRUCT, null);

    /**
     * @throws IllegalArgumentException when a {@code STEER} or {@code TRACK} command has no point, or another kind
     *                                  has one
     */
    public SteeringCommand {
        if (kind == null) {
            throw new IllegalArgumentException("a steering command needs a kind");
        }
        boolean needsPoint = kind == Kind.STEER || kind == Kind.TRACK;
        if (needsPoint != (point != null)) {
            throw new IllegalArgumentException(kind + (needsPoint ? " needs a point" : " takes no point"));
        }
    }

    /** Fly on as now. */
    public static SteeringCommand keepCourse() {
        return KEEP_COURSE;
    }

    /** Turn towards the point, as fast as the weapon's turn rate allows. */
    public static SteeringCommand steerTowards(Vec3 point) {
        return new SteeringCommand(Kind.STEER, point);
    }

    /**
     * Turn towards a point the seeker tracks (usually the target, possibly led), as fast as the turn rate allows;
     * the lock breaks when the point leaves the seeker cone.
     */
    public static SteeringCommand track(Vec3 point) {
        return new SteeringCommand(Kind.TRACK, point);
    }

    /** Drop the target and fly on. */
    public static SteeringCommand dropTarget() {
        return DROP_TARGET;
    }

    /** Set the warhead off now, where the projectile is. */
    public static SteeringCommand detonate() {
        return DETONATE;
    }

    /** Remove the projectile without exploding. */
    public static SteeringCommand selfDestruct() {
        return SELF_DESTRUCT;
    }
}
