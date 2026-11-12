package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.model.SimFakes.FixedTerrain;
import static com.ivomens.mcheli.core.sim.model.SimFakes.RecordingCollider;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TankModelTest {

    private static final float MOTION_FACTOR = 0.9F;
    private static final TankHandling HANDLING = new TankHandling(SimFakes.GRAVITY, 1.0F, MOTION_FACTOR, true, false,
            false);
    /** Sideways for a hull facing yaw 0, which drives towards +z. */
    private static final Vec3d SLIDING_SIDEWAYS = new Vec3d(0.1, 0.0, 0.0);
    private static final Vec3d ROLLING_FORWARD = new Vec3d(0.0, 0.0, 0.1);

    private final RecordingCollider collider = new RecordingCollider();

    private MotionStep drive(TankModel model, Vec3d velocity, TerrainProbe terrain) {
        return model.step(SimFakes.onGround(velocity), SimFakes.throttle(0.0), SimFakes.NO_KEYS, HANDLING,
                SimFakes.context(terrain, this.collider));
    }

    @Test
    void tracksGripSidewaysTwiceFirstBeforeTheMoveThenAfter() {
        MotionStep step = drive(new TrackedModel(), SLIDING_SIDEWAYS, FixedTerrain.SOLID_GROUND);

        assertEquals(0.1 * 0.35, this.collider.last().velocity().x(), 1.0E-15);
        assertEquals(0.1 * 0.35 * 0.20, step.body().velocity().x(), 1.0E-15);
    }

    @Test
    void tracksRollWithTheDefinitionsMotionFactor() {
        MotionStep step = drive(new TrackedModel(), ROLLING_FORWARD, FixedTerrain.SOLID_GROUND);

        assertEquals(0.1 * MOTION_FACTOR, this.collider.last().velocity().z(), 1.0E-9);
        assertEquals(0.1 * MOTION_FACTOR * MOTION_FACTOR, step.body().velocity().z(), 1.0E-9);
    }

    @Test
    void wheelsGripHarderSidewaysAndRollFreely() {
        MotionStep sliding = drive(new WheeledModel(), SLIDING_SIDEWAYS, FixedTerrain.SOLID_GROUND);
        assertEquals(0.1 * 0.18 * 0.10, sliding.body().velocity().x(), 1.0E-15);

        MotionStep rolling = drive(new WheeledModel(), ROLLING_FORWARD, FixedTerrain.SOLID_GROUND);
        assertEquals(0.1 * 0.985 * 0.992, rolling.body().velocity().z(), 1.0E-15);
    }

    @Test
    void anAirborneHullOnlyFeelsAirDragAfterTheMove() {
        MotionStep step = new TrackedModel().step(SimFakes.airborne(SLIDING_SIDEWAYS), SimFakes.throttle(0.0),
                SimFakes.NO_KEYS, HANDLING, SimFakes.context(FixedTerrain.OPEN_SKY, this.collider));

        assertEquals(0.1, this.collider.last().velocity().x());
        assertEquals(0.1 * MOTION_FACTOR, step.body().velocity().x(), 1.0E-9);
    }

    @Test
    void smallVerticalMotionIsDroppedOnTheGround() {
        ControlInput gunnerMode = new ControlInput(false, false, false, false, true);

        new TrackedModel().step(SimFakes.onGround(new Vec3d(0.0, 0.02, 0.0)), SimFakes.throttle(1.0), gunnerMode,
                HANDLING, SimFakes.context(FixedTerrain.SOLID_GROUND, this.collider));

        assertEquals(0.0, this.collider.last().velocity().y());
    }

    @Test
    void aHullRolledOnItsSideDoesNotFloatUp() {
        TankHandling floats = new TankHandling(SimFakes.GRAVITY, 1.0F, MOTION_FACTOR, true, false, true);
        BodyState capsized = SimFakes.airborne(new Vec3d(0.0, -0.1, 0.0))
                .withAttitude(new Attitude(0.0F, 0.0F, 90.0F));

        new TrackedModel().step(capsized, SimFakes.throttle(0.0), SimFakes.NO_KEYS, floats,
                SimFakes.context(FixedTerrain.water(1.0), this.collider));

        assertEquals(-0.1 - 1.0E-4, this.collider.last().velocity().y());
    }
}
