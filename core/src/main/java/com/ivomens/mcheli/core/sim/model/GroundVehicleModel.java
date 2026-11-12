package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;

/**
 * Emplaced guns and light ground vehicles. They drive by a fixed push per held key ({@link #drive})
 * and steer like a bicycle; unlike every other type they are damped before they move, not after.
 */
public final class GroundVehicleModel implements MotionModel<GroundVehicleHandling> {

    /**
     * Push per tick while a drive key is held. It is a float constant, so the push is
     * 0.03500000014901161, not the double 0.035 the throttle uses.
     */
    private static final float KEY_DRIVE_IMPULSE = 0.035F;
    /** Push per tick at full throttle. No key raises the throttle, so only an outside setting drives this. */
    private static final double THROTTLE_ACCELERATION = 0.035;
    private static final GroundGrip.Grip GRIP = new GroundGrip.Grip(0.08, 0.985);
    /** What a client predicts between server updates. */
    public static final GroundGrip.Grip PREDICTED_GRIP = new GroundGrip.Grip(0.12, 0.992);
    private static final double AIR_DRAG = 0.99;
    private static final double VERTICAL_DRAG = 0.95;

    /**
     * The driver's keys for one tick.
     *
     * @param velocity   the velocity with the key push added
     * @param yaw        the hull's yaw after steering
     * @param wheelAngle front wheel angle in -1..1 of full lock
     */
    public record Drive(Vec3d velocity, float yaw, float wheelAngle) {
    }

    /** Steers from the velocity before the push and pushes along the yaw before the turn. */
    public Drive drive(Vec3d velocity, float yaw, float wheelAngle, ControlInput input,
                       GroundVehicleHandling handling) {
        Vec3d pushed = handling.canDrive()
                ? KeyImpulse.push(velocity, yaw, input.withoutSideKeys(), KEY_DRIVE_IMPULSE) : velocity;
        if (!handling.canSteer()) {
            return new Drive(pushed, yaw, wheelAngle);
        }
        BicycleSteering.Steering steering = BicycleSteering.steer(wheelAngle, yaw, velocity, input);
        return new Drive(pushed, steering.yaw(), steering.wheelAngle());
    }

    @Override
    public MotionStep step(BodyState body, PropulsionState propulsion, ControlInput input,
                           GroundVehicleHandling handling, StepContext context) {
        float yaw = body.attitude().yaw();
        Vec3d velocity = body.velocity().withY(verticalForces(body, propulsion, handling, context.terrain()));
        if (propulsion.throttle() > 0.0) {
            Vec3d forward = LegacyMath.flatForward(yaw);
            double acceleration = THROTTLE_ACCELERATION * propulsion.throttle();
            velocity = new Vec3d(velocity.x() + forward.x() * acceleration, velocity.y(),
                    velocity.z() + forward.z() * acceleration);
        }
        velocity = SpeedLimit.clamp(velocity, handling.speedLimit()).velocity();
        velocity = body.onGround() ? GroundGrip.apply(velocity, yaw, GRIP) : velocity.scaleHorizontal(AIR_DRAG);
        // damped before the move, unlike every other type: the move itself goes a little shorter and
        // the velocity the collider leaves is kept as it is
        velocity = velocity.withY(velocity.y() * VERTICAL_DRAG);

        BodyState toMove = body.withVelocity(velocity);
        MoveResult moved = context.collider().move(toMove);
        return new MotionStep(toMove.after(moved, moved.velocity()), propulsion);
    }

    /** Gravity on land, buoyancy in water for vehicles that float. */
    private static double verticalForces(BodyState body, PropulsionState propulsion, GroundVehicleHandling handling,
                                         TerrainProbe terrain) {
        double motionY = body.velocity().y();
        if (!handling.floats() || body.wrecked()) {
            // a vehicle that cannot float ignores gravityInWater and sinks at its air gravity
            return motionY + handling.gravity().air();
        }
        double waterDepth = terrain.waterDepth();
        if (waterDepth == 0.0) {
            return VerticalForces.gravity(motionY, body.inWater(), handling.gravity());
        }
        return VerticalForces.floating(motionY, waterDepth, propulsion.throttle(), VerticalForces.HULL_SINK_DAMPING);
    }
}
