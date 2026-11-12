package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.model.SimFakes.FixedTerrain;
import static com.ivomens.mcheli.core.sim.model.SimFakes.RecordingCollider;
import static com.ivomens.mcheli.core.sim.model.SimFakes.ScriptedRandom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelicopterModelTest {

    private static final RotorcraftHandling HANDLING = new RotorcraftHandling(SimFakes.GRAVITY, 1.0F, 0.0F, false);
    private static final ControlInput HOVER = new ControlInput(false, false, false, false, true);

    private final HelicopterModel model = new HelicopterModel();
    private final RecordingCollider collider = new RecordingCollider();

    private MotionStep fly(BodyState body, PropulsionState propulsion, ControlInput input) {
        return this.model.step(body, propulsion, input, HANDLING, SimFakes.context(FixedTerrain.OPEN_SKY,
                this.collider));
    }

    @Test
    void fullThrottleClimbsWhenLevel() {
        MotionStep step = fly(SimFakes.airborne(Vec3d.ZERO), SimFakes.throttle(1.0), SimFakes.NO_KEYS);

        double lift = (1.0 * 0.025 + 0.03) * 1.0;
        assertEquals(((0.0 + -0.04F) + lift) * 0.95, step.body().velocity().y());
        assertTrue(step.body().velocity().y() > 0.0);
    }

    @Test
    void idleRotorFallsAtGravity() {
        MotionStep step = fly(SimFakes.airborne(Vec3d.ZERO), SimFakes.throttle(0.0), SimFakes.NO_KEYS);

        assertEquals((0.0 + -0.04F) * 0.95, step.body().velocity().y());
    }

    @Test
    void tiltingTheDiscForwardPullsAlongTheNose() {
        BodyState noseDown = SimFakes.airborne(Vec3d.ZERO).withAttitude(new Attitude(0.0F, 20.0F, 0.0F));

        MotionStep step = fly(noseDown, SimFakes.throttle(1.0), SimFakes.NO_KEYS);

        // yaw 0 faces +z
        assertTrue(step.body().velocity().z() > 0.0);
        assertEquals(0.0, step.body().velocity().x(), 1.0E-12);
    }

    @Test
    void hoverDriftDrawsFromTheInjectedRandomInAxisOrder() {
        ScriptedRandom random = new ScriptedRandom().ints(0, 1, 0).doubles(0.8, 0.2);
        StepContext context = new StepContext(FixedTerrain.OPEN_SKY, this.collider, random);

        this.model.step(SimFakes.airborne(Vec3d.ZERO), SimFakes.throttle(1.0), HOVER, HANDLING, context);

        Vec3d planned = this.collider.last().velocity();
        assertEquals((0.8 - 0.5) / 30.0, planned.x());
        assertEquals(0.0, planned.y());
        assertEquals((0.2 - 0.5) / 30.0, planned.z());
        assertEquals(5, random.draws());
    }

    @Test
    void aWreckLosesItsHorizontalMotionAndCruiseSpeed() {
        BodyState wreck = new BodyState(new Vec3d(0.2, 0.0, 0.2), SimFakes.LEVEL, false, false, true);

        MotionStep step = fly(wreck, new PropulsionState(0.5, 0.0F, 0.5, 0.0F), SimFakes.NO_KEYS);

        assertEquals(0.0, this.collider.last().velocity().x());
        assertEquals(0.0, this.collider.last().velocity().z());
        assertEquals(0.07, step.propulsion().cruiseSpeed());
    }

    @Test
    void onTheGroundFrictionHalvesTheSlideAndTheAirframeSettles() {
        BodyState parked = SimFakes.onGround(new Vec3d(0.1, 0.0, 0.0)).withAttitude(new Attitude(0.0F, 10.0F, 10.0F));

        fly(parked, SimFakes.throttle(0.0), SimFakes.NO_KEYS);

        BodyState moved = this.collider.last();
        assertEquals(0.1 * 0.5, moved.velocity().x());
        assertEquals(new Attitude(0.0F, 10.0F * 0.9F, 10.0F * 0.9F), moved.attitude());
    }

    @Test
    void floatingHullsRiseInDeepWater() {
        RotorcraftHandling floats = new RotorcraftHandling(SimFakes.GRAVITY, 1.0F, 0.0F, true);
        StepContext deepWater = SimFakes.context(FixedTerrain.water(1.0), this.collider);

        MotionStep step = this.model.step(SimFakes.airborne(new Vec3d(0.0, -0.1, 0.0)), SimFakes.throttle(0.0),
                SimFakes.NO_KEYS, floats, deepWater);

        assertEquals((-0.1 * 0.7 + 0.007) * 0.95, step.body().velocity().y());
    }
}
