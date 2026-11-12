package com.ivomens.mcheli.core.sim.model;

/**
 * Wheeled vehicles ({@code weightType} car): tyres grip harder sideways than tracks and roll
 * freely, whatever the definition's {@code motionFactor}.
 */
public final class WheeledModel extends TankModel {

    private static final GroundGrip.Grip GRIP_BEFORE_MOVE = new GroundGrip.Grip(0.18, 0.985);
    private static final GroundGrip.Grip GRIP_AFTER_MOVE = new GroundGrip.Grip(0.10, 0.992);
    /** What a client predicts between server updates. */
    public static final GroundGrip.Grip PREDICTED_GRIP = new GroundGrip.Grip(0.14, 0.99);

    @Override
    protected GroundGrip.Grip gripBeforeMove(TankHandling handling) {
        return GRIP_BEFORE_MOVE;
    }

    @Override
    protected GroundGrip.Grip gripAfterMove(TankHandling handling) {
        return GRIP_AFTER_MOVE;
    }
}
