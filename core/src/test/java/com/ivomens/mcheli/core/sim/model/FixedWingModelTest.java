package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.model.SimFakes.FixedTerrain;
import static com.ivomens.mcheli.core.sim.model.SimFakes.RecordingCollider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWingModelTest {

    private static final float MOTION_FACTOR = 0.96F;
    private static final FixedWingHandling HANDLING = new FixedWingHandling(SimFakes.GRAVITY, 2.0F, MOTION_FACTOR,
            0.0F, true, false, false);
    private static final ControlInput GUNNER_MODE = new ControlInput(false, false, false, false, true);

    private final FixedWingModel model = new FixedWingModel();
    private final RecordingCollider collider = new RecordingCollider();

    private MotionStep fly(BodyState body, PropulsionState propulsion, ControlInput input, TerrainProbe terrain) {
        return this.model.step(body, propulsion, input, HANDLING, SimFakes.context(terrain, this.collider));
    }

    @Test
    void withTheThrottleCutThePlaneSinksByTheVerticalLaw() {
        MotionStep step = fly(SimFakes.airborne(new Vec3d(0.0, -0.1, 0.0)), SimFakes.throttle(0.0),
                SimFakes.NO_KEYS, FixedTerrain.OPEN_SKY);

        double law = -0.1 + (0.04 + -0.04F) - 0.047 * (1.0 - 0.0);
        assertEquals(law, this.collider.last().velocity().y());
        assertEquals(law * 0.95, step.body().velocity().y());
    }

    @Test
    void fullThrottleCancelsGravityAndTheNoseThrustClimbs() {
        fly(SimFakes.airborne(Vec3d.ZERO), SimFakes.throttle(1.0), SimFakes.NO_KEYS, FixedTerrain.OPEN_SKY);

        // thrust points ten degrees above the nose; half of its vertical part lifts (the table sine is coarse)
        double thrustLift = -Math.sin(Math.toRadians(-10.0)) * 0.1 / 2.0;
        assertEquals(thrustLift, this.collider.last().velocity().y(), 1.0E-5);
        assertTrue(this.collider.last().velocity().z() > 0.09);
    }

    @Test
    void gunnerModeHoldsAltitudeWithoutThrustLift() {
        fly(SimFakes.airborne(new Vec3d(0.0, -0.1, 0.0)), SimFakes.throttle(1.0), GUNNER_MODE,
                FixedTerrain.OPEN_SKY);

        assertEquals(-0.1 * 0.8, this.collider.last().velocity().y());
    }

    @Test
    void vectoredThrustPitchesTheNoseDownOncePerStep() {
        BodyState noseUp = SimFakes.airborne(Vec3d.ZERO).withAttitude(new Attitude(0.0F, 10.0F, 0.0F));

        fly(noseUp, new PropulsionState(1.0, 0.0F, 0.07, 45.0F), SimFakes.NO_KEYS, FixedTerrain.OPEN_SKY);

        assertEquals(10.0F * 0.95F, this.collider.last().attitude().pitch());
    }

    @Test
    void horizontalNozzlesLeaveThePitchAlone() {
        BodyState noseUp = SimFakes.airborne(Vec3d.ZERO).withAttitude(new Attitude(0.0F, 10.0F, 0.0F));

        fly(noseUp, SimFakes.throttle(1.0), SimFakes.NO_KEYS, FixedTerrain.OPEN_SKY);

        assertEquals(10.0F, this.collider.last().attitude().pitch());
    }

    @Test
    void rollingOnTheGroundDragsByTheMotionFactorBeforeAndAfterTheMove() {
        MotionStep step = fly(SimFakes.onGround(new Vec3d(0.0, 0.0, 1.0)), SimFakes.throttle(0.0),
                SimFakes.NO_KEYS, FixedTerrain.SOLID_GROUND);

        double planned = 1.0 * (double) MOTION_FACTOR;
        assertEquals(planned, this.collider.last().velocity().z(), 1.0E-6);
        assertEquals(planned * MOTION_FACTOR, step.body().velocity().z(), 1.0E-6);
    }

    @Test
    void reverseThrustPushesBackwardsWhenTheDefinitionHasIt() {
        FixedWingHandling reversible = new FixedWingHandling(SimFakes.GRAVITY, 2.0F, MOTION_FACTOR, 0.0F, true,
                true, false);
        PropulsionState reversing = new PropulsionState(0.0, 0.5F, 0.07, 0.0F);

        this.model.step(SimFakes.airborne(Vec3d.ZERO), reversing, SimFakes.NO_KEYS, reversible,
                SimFakes.context(FixedTerrain.OPEN_SKY, this.collider));

        assertTrue(this.collider.last().velocity().z() < 0.0);
    }
}
