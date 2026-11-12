package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.CruiseSpeed;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;

/**
 * Hull-driven ground vehicles: the engine pushes along the hull, and on the ground the tracks or
 * wheels grip twice, once before the move and once after. The two kinds differ only in how firmly
 * they grip; their steering and terrain following are attitude, not motion.
 */
public abstract sealed class TankModel implements MotionModel<TankHandling> permits TrackedModel, WheeledModel {

    /** Thrust only lifts an airborne hull once it moves faster than this, so a jump carries on a bit. */
    private static final double AIRBORNE_SPEED_MIN = 0.05;
    private static final double AIRBORNE_LIFT_DIVISOR = 8.0;
    /** In the air the engine pushes this far above the hull's nose. */
    private static final float AIRBORNE_THRUST_ABOVE_NOSE = 10.0F;
    /** On the ground smaller vertical speeds are dropped, so the hull does not jitter on its suspension. */
    private static final double SMALL_MOTION_Y = 0.03;
    /** A hull rolled further than this onto its side does not float up. */
    private static final float UPRIGHT_ROLL = 40.0F;
    private static final double VERTICAL_DRAG = 0.95;

    /** Grip applied to the planned motion on the ground, before the move. */
    protected abstract GroundGrip.Grip gripBeforeMove(TankHandling handling);

    /** Grip applied to what is left after the move, on the ground. */
    protected abstract GroundGrip.Grip gripAfterMove(TankHandling handling);

    @Override
    public final MotionStep step(BodyState body, PropulsionState propulsion, ControlInput input,
                                 TankHandling handling, StepContext context) {
        TerrainProbe terrain = context.terrain();
        Vec3d start = body.velocity();
        double motionY = verticalForces(body, propulsion, input, handling, terrain);
        double prevSpeed = start.horizontalLength();
        boolean onSurface = body.onGround() || !terrain.isClearBelow(1, 2);

        float thrust = EngineThrust.perTick(propulsion.throttle());
        Vec3d direction = thrustDirection(body.attitude(), onSurface);
        if (!input.autopilot() && !body.onGround() && prevSpeed > AIRBORNE_SPEED_MIN) {
            motionY += direction.y() * thrust / AIRBORNE_LIFT_DIVISOR;
        }
        Vec3d velocity = start.withY(motionY);
        if (handling.canMoveOnGround() || terrain.isAboveWaterOrAir()) {
            velocity = EngineThrust.pushHorizontally(velocity, direction, thrust, propulsion.reverseThrust(),
                    handling.reverseEnabled());
        }

        SpeedLimit.Limited limited = SpeedLimit.clamp(velocity, handling.maxSpeed());
        double cruiseSpeed = CruiseSpeed.approach(propulsion.cruiseSpeed(), limited.speed() > prevSpeed,
                handling.maxSpeed());
        float yaw = body.attitude().yaw();
        velocity = limited.velocity();
        if (onSurface) {
            velocity = dropSmallVerticalMotion(GroundGrip.apply(velocity, yaw, gripBeforeMove(handling)));
        }

        BodyState toMove = body.withVelocity(velocity);
        MoveResult moved = context.collider().move(toMove);
        Vec3d after = moved.velocity().withY(moved.velocity().y() * VERTICAL_DRAG);
        after = onSurface ? GroundGrip.apply(after, yaw, gripAfterMove(handling))
                : after.scaleHorizontal(handling.motionFactor());
        return new MotionStep(toMove.after(moved, after), propulsion.withCruiseSpeed(cruiseSpeed));
    }

    /** Gravity on land, buoyancy in water. */
    private static double verticalForces(BodyState body, PropulsionState propulsion, ControlInput input,
                                         TankHandling handling, TerrainProbe terrain) {
        double waterDepth = handling.floats() && !body.wrecked() ? terrain.waterDepth() : 0.0;
        double motionY = body.velocity().y();
        if (waterDepth == 0.0) {
            return VerticalForces.unsupported(motionY, input.autopilot(), body.inWater(), handling.gravity(),
                    propulsion.throttle());
        }
        boolean upright = Math.abs(body.attitude().roll()) < UPRIGHT_ROLL;
        if (upright && waterDepth >= 1.0) {
            return VerticalForces.deepWater(motionY, VerticalForces.HULL_SINK_DAMPING);
        }
        return VerticalForces.shallowWater(motionY, propulsion.throttle());
    }

    /** Flat along the hull on the ground, slightly nose-up in the air. */
    private static Vec3d thrustDirection(Attitude attitude, boolean onSurface) {
        Vec3d direction = onSurface ? LegacyMath.flatForward(attitude.yaw())
                : LegacyMath.lookVector(attitude.yaw(), attitude.pitch() - AIRBORNE_THRUST_ABOVE_NOSE);
        return direction.normalized();
    }

    /**
     * The legacy ground settle also eased pitch, but tanks take their pitch from the wheels and
     * ignore that request, so only the vertical part of it remains.
     */
    private static Vec3d dropSmallVerticalMotion(Vec3d velocity) {
        return Math.abs(velocity.y()) < SMALL_MOTION_Y ? velocity.withY(0.0) : velocity;
    }
}
