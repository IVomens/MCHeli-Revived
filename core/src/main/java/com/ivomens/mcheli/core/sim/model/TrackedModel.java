package com.ivomens.mcheli.core.sim.model;

/**
 * Tracked tanks: tracks hold firmly sideways and roll with the definition's {@code motionFactor}.
 */
public final class TrackedModel extends TankModel {

    private static final double LATERAL_GRIP_BEFORE_MOVE = 0.35;
    private static final double LATERAL_GRIP_AFTER_MOVE = 0.20;
    /** What a client predicts between server updates; a little looser than the real thing. */
    public static final GroundGrip.Grip PREDICTED_GRIP = new GroundGrip.Grip(0.25, 0.97);

    @Override
    protected GroundGrip.Grip gripBeforeMove(TankHandling handling) {
        return new GroundGrip.Grip(LATERAL_GRIP_BEFORE_MOVE, handling.motionFactor());
    }

    @Override
    protected GroundGrip.Grip gripAfterMove(TankHandling handling) {
        return new GroundGrip.Grip(LATERAL_GRIP_AFTER_MOVE, handling.motionFactor());
    }
}
