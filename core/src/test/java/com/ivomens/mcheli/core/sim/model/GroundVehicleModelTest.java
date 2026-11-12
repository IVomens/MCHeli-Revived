package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import org.junit.jupiter.api.Test;

import static com.ivomens.mcheli.core.sim.model.SimFakes.FixedTerrain;
import static com.ivomens.mcheli.core.sim.model.SimFakes.RecordingCollider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundVehicleModelTest {

    private static final GroundVehicleHandling HANDLING = new GroundVehicleHandling(SimFakes.GRAVITY, 1.0F, false,
            true, true);
    private static final ControlInput FORWARD = new ControlInput(true, false, false, false, false);
    private static final ControlInput FORWARD_RIGHT = new ControlInput(true, false, false, true, false);

    private final GroundVehicleModel model = new GroundVehicleModel();
    private final RecordingCollider collider = new RecordingCollider();

    @Test
    void dampsBeforeTheMoveAndKeepsWhatTheColliderLeaves() {
        MotionStep step = this.model.step(SimFakes.airborne(new Vec3d(0.0, -0.5, 0.0)), SimFakes.throttle(0.0),
                SimFakes.NO_KEYS, HANDLING, SimFakes.context(FixedTerrain.OPEN_SKY, this.collider));

        double fallen = (-0.5 + -0.04F) * 0.95;
        assertEquals(fallen, this.collider.last().velocity().y());
        assertEquals(fallen, step.body().velocity().y());
    }

    @Test
    void aHeldKeyPushesByTheFloatConstant() {
        GroundVehicleModel.Drive drive = this.model.drive(Vec3d.ZERO, 0.0F, 0.0F, FORWARD, HANDLING);

        assertEquals((double) 0.035F, drive.velocity().z(), 1.0E-15);
    }

    @Test
    void steeringNeedsTheVehicleToRoll() {
        GroundVehicleModel.Drive standing = this.model.drive(Vec3d.ZERO, 0.0F, 0.0F, FORWARD_RIGHT, HANDLING);
        assertEquals(0.0F, standing.yaw());
        assertEquals(0.14F, standing.wheelAngle());

        GroundVehicleModel.Drive rolling = this.model.drive(new Vec3d(0.0, 0.0, 0.3), 0.0F, 0.0F, FORWARD_RIGHT,
                HANDLING);
        assertTrue(rolling.yaw() > 0.0F);
    }

    @Test
    void withoutDriveOrSteeringTheKeysDoNothing() {
        GroundVehicleHandling emplaced = new GroundVehicleHandling(SimFakes.GRAVITY, 1.0F, false, false, false);
        Vec3d velocity = new Vec3d(0.0, 0.0, 0.3);

        GroundVehicleModel.Drive drive = this.model.drive(velocity, 0.0F, 0.0F, FORWARD_RIGHT, emplaced);

        assertSame(velocity, drive.velocity());
        assertEquals(0.0F, drive.yaw());
    }
}
