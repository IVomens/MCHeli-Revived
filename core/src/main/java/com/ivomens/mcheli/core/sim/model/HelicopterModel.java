package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.SimRandom;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.CruiseSpeed;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;

/**
 * Helicopter flight. The rotor lifts in proportion to throttle, a little more when nearly level,
 * and tilting the disc turns lift into thrust with the cube of the pitch. Hovering replaces all of
 * that with a gentle random drift; strafing is applied by the control step before this runs.
 */
public final class HelicopterModel implements MotionModel<RotorcraftHandling> {

    /** Each axis gets a drift nudge on average once in this many ticks. */
    private static final int DRIFT_ODDS = 50;
    private static final double DRIFT_HORIZONTAL_DIVISOR = 30.0;
    private static final double DRIFT_VERTICAL_DIVISOR = 50.0;
    private static final double TILT_THRUST_GAIN = 0.1;
    private static final float TILT_THRUST_DIVISOR = 20000.0F;
    private static final float LEVEL_FLIGHT_TILT = 40.0F;
    private static final float LEVEL_ROLL_WEIGHT = 0.9F;
    private static final double LEVEL_LIFT_BONUS = 0.025;
    private static final double BASE_LIFT = 0.03;
    /** A wrecked rotor pulls the airframe down instead of up. */
    private static final double WRECK_LIFT = -0.65;
    private static final double GROUND_FRICTION = 0.5;
    private static final double AIR_DRAG = 0.99;
    private static final double VERTICAL_DRAG = 0.95;

    /**
     * The throttle at which a level helicopter in powered flight settles at a climb rate. Each tick
     * it moves by its speed plus gravity and lift, and then keeps {@code VERTICAL_DRAG} of that, so it
     * rises by the same {@code d} every tick where {@code d (1 - drag) = g + lift t}. Not used by the
     * model itself; the altitude hold of the helicopter assist aims with it.
     *
     * @param gravityInAir the definition's gravity in air, per tick (negative)
     * @param climbPerTick the height wanted per tick, blocks
     */
    public static double throttleForClimb(float gravityInAir, double climbPerTick) {
        double lift = LEVEL_LIFT_BONUS + BASE_LIFT;
        return (climbPerTick * (1.0 - VERTICAL_DRAG) - gravityInAir) / lift;
    }

    /**
     * The disc pitch whose tilt thrust adds {@code acceleration} to the speed along the nose each tick,
     * the inverse of the tilt law in powered flight away from the ground. Not used by the model itself;
     * the wingman autopilot flies formation with it.
     *
     * @param acceleration blocks per tick per tick, negative to slow down
     * @param cruiseSpeed  the rotor's cruise speed, which scales the tilt thrust
     * @param throttle     the throttle, which scales it too
     * @return the pitch in degrees (positive is nose down); NaN or infinite without thrust to tilt
     */
    public static float pitchForAcceleration(double acceleration, double cruiseSpeed, double throttle) {
        double thrust = TILT_THRUST_GAIN * cruiseSpeed * throttle;
        return (float) Math.cbrt(acceleration / thrust * TILT_THRUST_DIVISOR);
    }

    @Override
    public MotionStep step(BodyState body, PropulsionState propulsion, ControlInput input,
                           RotorcraftHandling handling, StepContext context) {
        double prevSpeed = body.velocity().horizontalLength();
        BodyState forced = input.autopilot() ? hoverDrift(body, context.random())
                : rotorForces(body, propulsion, handling, context.terrain());

        SpeedLimit.Limited limited = SpeedLimit.clamp(forced.velocity(), handling.speedLimit());
        Vec3d velocity = limited.velocity();
        double cruiseSpeed = propulsion.cruiseSpeed();
        if (body.wrecked()) {
            velocity = new Vec3d(0.0, velocity.y(), 0.0);
            cruiseSpeed = 0.0;
        }
        cruiseSpeed = CruiseSpeed.approach(cruiseSpeed, limited.speed() > prevSpeed, handling.speedLimit());

        Attitude attitude = forced.attitude();
        if (body.onGround()) {
            velocity = velocity.scaleHorizontal(GROUND_FRICTION);
            attitude = GroundSettle.settleRotorcraft(attitude, handling.onGroundPitch());
        }

        BodyState toMove = forced.withVelocity(velocity).withAttitude(attitude);
        MoveResult moved = context.collider().move(toMove);
        Vec3d damped = moved.velocity().scale(AIR_DRAG, VERTICAL_DRAG, AIR_DRAG);
        return new MotionStep(toMove.after(moved, damped), propulsion.withCruiseSpeed(cruiseSpeed));
    }

    /** Small random nudges keep a hovering helicopter from looking frozen. */
    private static BodyState hoverDrift(BodyState body, SimRandom random) {
        Vec3d velocity = body.velocity();
        double motionX = velocity.x();
        double motionY = velocity.y();
        double motionZ = velocity.z();
        if (random.nextInt(DRIFT_ODDS) == 0) {
            motionX += (random.nextDouble() - 0.5) / DRIFT_HORIZONTAL_DIVISOR;
        }
        if (random.nextInt(DRIFT_ODDS) == 0) {
            motionY += (random.nextDouble() - 0.5) / DRIFT_VERTICAL_DIVISOR;
        }
        if (random.nextInt(DRIFT_ODDS) == 0) {
            motionZ += (random.nextDouble() - 0.5) / DRIFT_HORIZONTAL_DIVISOR;
        }
        return body.withVelocity(new Vec3d(motionX, motionY, motionZ));
    }

    private static BodyState rotorForces(BodyState body, PropulsionState propulsion, RotorcraftHandling handling,
                                         TerrainProbe terrain) {
        double waterDepth = handling.floats() && !body.wrecked() ? terrain.waterDepth() : 0.0;
        if (waterDepth != 0.0) {
            double motionY = VerticalForces.floating(body.velocity().y(), waterDepth, propulsion.throttle(),
                    VerticalForces.ROTOR_SINK_DAMPING);
            return body.withVelocity(body.velocity().withY(motionY))
                    .withAttitude(GroundSettle.settleRotorcraft(body.attitude(), handling.onGroundPitch()));
        }
        return body.withVelocity(poweredFlight(body, propulsion, handling, terrain));
    }

    private static Vec3d poweredFlight(BodyState body, PropulsionState propulsion, RotorcraftHandling handling,
                                       TerrainProbe terrain) {
        Vec3d velocity = body.velocity();
        Attitude attitude = body.attitude();
        double throttle = propulsion.throttle();
        double motionY = VerticalForces.gravity(velocity.y(), body.inWater(), handling.gravity());

        // close to the ground the disc tilt counts from the resting pitch, so a parked nose-up
        // helicopter does not taxi backwards
        float discPitch = attitude.pitch();
        if (!terrain.isClearBelow(3, 3)) {
            discPitch -= handling.onGroundPitch();
        }
        float tilt = discPitch * discPitch * discPitch / TILT_THRUST_DIVISOR;
        float yawRad = LegacyMath.floatRadians(attitude.yaw());
        double cruiseSpeed = propulsion.cruiseSpeed();
        double motionX = velocity.x() + TILT_THRUST_GAIN * LegacyMath.sin(yawRad) * cruiseSpeed * -tilt * throttle;
        double motionZ = velocity.z() + TILT_THRUST_GAIN * LegacyMath.cos(yawRad) * cruiseSpeed * tilt * throttle;

        boolean nearlyLevel = Math.abs(attitude.pitch()) + Math.abs(attitude.roll() * LEVEL_ROLL_WEIGHT)
                <= LEVEL_FLIGHT_TILT;
        double levelBonus = nearlyLevel ? 1.0 : 0.0;
        double lift = body.wrecked() ? throttle * WRECK_LIFT : throttle;
        motionY += (levelBonus * LEVEL_LIFT_BONUS + BASE_LIFT) * lift;
        return new Vec3d(motionX, motionY, motionZ);
    }
}
