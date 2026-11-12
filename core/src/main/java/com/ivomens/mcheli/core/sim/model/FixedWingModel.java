package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.CruiseSpeed;
import com.ivomens.mcheli.core.sim.propulsion.NozzleModel;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;

/**
 * Planes, ships and VTOLs. There is no aerodynamic lift: the vertical law sinks the airframe less
 * the higher the throttle, and thrust along the nose (or down the VTOL nozzles) does the rest.
 * Gunner mode and target-drone cruise hold altitude instead.
 */
public final class FixedWingModel implements MotionModel<FixedWingHandling> {

    /** Rolling out on the gear levels the airframe this much per tick. */
    private static final float ROLLOUT_LEVELING = 0.8F;
    private static final float ROLLOUT_LEVELING_LIMIT = 40.0F;
    private static final double VERTICAL_DRAG = 0.95;

    @Override
    public MotionStep step(BodyState body, PropulsionState propulsion, ControlInput input,
                           FixedWingHandling handling, StepContext context) {
        TerrainProbe terrain = context.terrain();
        double prevSpeed = body.velocity().horizontalLength();
        double waterDepth = handling.floats() && !body.wrecked() ? terrain.waterDepth() : 0.0;
        boolean holdAltitude = input.autopilot();
        Attitude attitude = waterDepth == 0.0 ? body.attitude() : GroundSettle.levelAfloat(body.attitude());
        double motionY = verticalForces(body, propulsion, handling, waterDepth, holdAltitude);

        float thrust = EngineThrust.perTick(propulsion.throttle());
        NozzleModel.Thrust vectored = NozzleModel.thrust(attitude, propulsion.nozzleAngle());
        Vec3d direction = vectored.direction();
        if (!holdAltitude) {
            motionY += direction.y() * thrust / NozzleModel.liftDivisor(propulsion.nozzleAngle());
        }
        Vec3d velocity = body.velocity().withY(motionY);
        if (handling.canMoveOnGround() || terrain.isAboveWaterOrAir()) {
            velocity = EngineThrust.pushHorizontally(velocity, direction, thrust, propulsion.reverseThrust(),
                    handling.reverseEnabled());
        }

        SpeedLimit.Limited limited = SpeedLimit.clamp(velocity, handling.maxSpeed());
        double cruiseSpeed = CruiseSpeed.approach(propulsion.cruiseSpeed(), limited.speed() > prevSpeed,
                handling.maxSpeed());
        BodyState toMove = rollOut(body.withVelocity(limited.velocity()).withAttitude(vectored.attitude()),
                handling, terrain);

        MoveResult moved = context.collider().move(toMove);
        double friction = handling.motionFactor();
        Vec3d damped = moved.velocity().scale(friction, VERTICAL_DRAG, friction);
        return new MotionStep(toMove.after(moved, damped), propulsion.withCruiseSpeed(cruiseSpeed));
    }

    private static double verticalForces(BodyState body, PropulsionState propulsion, FixedWingHandling handling,
                                         double waterDepth, boolean holdAltitude) {
        double motionY = body.velocity().y();
        if (waterDepth != 0.0) {
            return VerticalForces.floating(motionY, waterDepth, propulsion.throttle(),
                    VerticalForces.HULL_SINK_DAMPING);
        }
        return VerticalForces.unsupported(motionY, holdAltitude, body.inWater(), handling.gravity(),
                propulsion.throttle());
    }

    /** On or just above the ground the wheels drag and the airframe levels onto its gear. */
    private static BodyState rollOut(BodyState body, FixedWingHandling handling, TerrainProbe terrain) {
        if (!body.onGround() && terrain.isClearBelow(1, 2)) {
            return body;
        }
        Attitude attitude = body.attitude();
        if (Math.abs(attitude.pitch()) < ROLLOUT_LEVELING_LIMIT) {
            attitude = GroundSettle.level(attitude, handling.onGroundPitch(), ROLLOUT_LEVELING);
        }
        return body.withVelocity(body.velocity().scaleHorizontal(handling.motionFactor())).withAttitude(attitude);
    }
}
