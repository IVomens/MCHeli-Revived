package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.math.ZxyRotation;
import com.ivomens.mcheli.core.sim.model.GroundGrip;

/**
 * Tank and car hull attitude. The mouse aims the turret, never the hull: pitch and roll follow the
 * slope the wheels report, and the side keys steer, cars like cars and tracks by pivoting.
 */
public final class GroundAttitude implements AttitudeModel<GroundAttitude.Setup, FrameTimeAverage> {

    /** Steps longer than this (a hitch, a pause) count as this long. */
    private static final float MAX_STEP_SECONDS = 0.1F;
    private static final float RIGHT_ANGLE = 90.0F;
    private static final double GUNNER_LEVELLING_PER_TICK = 0.95;
    private static final float GUNNER_ROLL_LIMIT = 20.0F;
    private static final float GUNNER_TURN_PER_AUTOPILOT = 0.2F;
    private static final double MOVING_SPEED = 0.08;
    /** Tracks pivot at this many degrees per second, cars turn at up to {@link #CAR_MAX_STEER_RATE}. */
    private static final float TRACK_YAW_RATE = 12.0F;
    private static final double CAR_MIN_STEER_RATE = 18.0;
    private static final double CAR_MAX_STEER_RATE = 55.0;
    private static final double CAR_STEER_RATE_PER_SPEED = 90.0;
    /** Below this speed a car steers as if creeping in the direction of the held throttle key. */
    private static final double CAR_CREEP_SPEED = 0.02;
    private static final double CAR_STOPPED_SPEED = 0.001;
    /** Low throttle with only the brake key held means reversing, which mirrors the steering. */
    private static final double REVERSE_THROTTLE_MARGIN = 0.05;
    private static final float REVERSE_THRUST_PIVOT_SHARE = 10.0F;
    private static final double REVERSE_MOVE_PER_THRUST = 0.01;
    /** Degrees per tick a tracked hull turns while backing up. */
    private static final float REVERSE_STEER_PER_TICK = 0.6F;

    /** How fast the hull follows the slope: pitch then roll, on the ground (moving, still) and airborne. */
    private static final Follow PITCH_FOLLOW = new Follow(10.0F, 7.0F, 4.0F, 24.0F);
    private static final Follow ROLL_FOLLOW = new Follow(9.0F, 6.0F, 3.5F, 20.0F);
    /** On the ground the hull may follow a step in the slope at up to this many degrees per second. */
    private static final float GROUND_FOLLOW_MAX_RATE = 90.0F;

    /**
     * What a tank reads for one step.
     *
     * @param car               wheeled, steering like a car; otherwise tracked
     * @param groundTurning     how steering works on solid ground
     * @param pivotTurnThrottle throttle a tracked hull needs before it pivots; 0 pivots at rest
     * @param floats            whether the hull floats, so it steers on water like on ground
     * @param limitRotation     the definition's {@code limitRotation}: tanks clamp to +/-90 regardless
     *                          of the limits it gives
     * @param autoPilotRot      the slow turn of gunner mode
     * @param gunnerMode        whether gunner mode is on
     * @param motion            what the hull is doing
     * @param slope             the attitude the wheels want
     * @param recoil            the current gun recoil
     */
    public record Setup(boolean car, GroundTurning groundTurning, float pivotTurnThrottle, boolean floats,
                        boolean limitRotation, float autoPilotRot, boolean gunnerMode, Motion motion, Slope slope,
                        RecoilKick recoil) {
    }

    /**
     * @param velocity      blocks per tick
     * @param movedSquared  horizontal distance moved this tick, squared
     * @param throttle      current throttle
     * @param reverseThrust current reverse thrust
     */
    public record Motion(Vec3d velocity, double movedSquared, double throttle, float reverseThrust) {
    }

    /** The pitch and roll the wheels would give the hull on the ground below it. */
    public record Slope(float pitch, float roll) {
    }

    private record Follow(float movingRate, float stillRate, float airborneRate, float airborneMaxRate) {
    }

    @Override
    public FrameTimeAverage initialMemory() {
        return FrameTimeAverage.empty();
    }

    @Override
    public AttitudeState<FrameTimeAverage> integrate(AttitudeState<FrameTimeAverage> state, AttitudeInput mouse,
                                                     ControlInput keys, Setup setup, AttitudeContext context,
                                                     float dtSeconds) {
        FrameTimeAverage stepTimes = state.memory().with(LegacyMath.clamp(dtSeconds, 0.0F, MAX_STEP_SECONDS));
        // rebuilding through the rotation folds a pitch past +/-90 back into range
        Attitude rebuilt = ZxyRotation.rebuild(state.attitude());
        Attitude attitude = new Attitude(rebuilt.yaw(), withinRightAngle(rebuilt.pitch()),
                withinRightAngle(rebuilt.roll()));
        attitude = settle(attitude, keys, setup, context, stepTimes.average());
        if (setup.limitRotation()) {
            attitude = new Attitude(attitude.yaw(), withinRightAngle(attitude.pitch()),
                    withinRightAngle(attitude.roll()));
        }
        if (Math.abs(attitude.pitch()) > RIGHT_ANGLE) {
            attitude = attitude.withPitch(0.0F);
        }
        attitude = attitude.withRoll(StickIntegration.wrapRoll(attitude.roll()));
        return new AttitudeState<>(attitude, state.orientation(), stepTimes);
    }

    @Override
    public float maxTurnRate(Setup setup) {
        float gunnerTurn = Math.abs(setup.autoPilotRot()) * GUNNER_TURN_PER_AUTOPILOT * PerTick.TICKS_PER_SECOND;
        float reverseSteer = REVERSE_STEER_PER_TICK * PerTick.TICKS_PER_SECOND;
        return (float) CAR_MAX_STEER_RATE + reverseSteer + 2.0F * GROUND_FOLLOW_MAX_RATE + gunnerTurn;
    }

    /**
     * A tracked hull backing up turns 0.6 degrees per tick for a held side key, with the keys
     * mirrored like a reversing car. Runs once per tick, on the driver's client only.
     */
    public static float steerWhileReversing(float yaw, ControlInput keys, Setup setup, AttitudeTerrain terrain) {
        Motion motion = setup.motion();
        float pivotThrottle = setup.pivotTurnThrottle();
        if (pivotThrottle <= 0.0F || setup.car() || motion.reverseThrust() <= 0.0F) {
            return yaw;
        }
        float speedFactor = (float) Math.sqrt(Math.min(motion.movedSquared(), 1.0));
        float mobility = setup.groundTurning().factor(terrain.airborne(), terrain.aboveWaterOrAir());
        float turn = REVERSE_STEER_PER_TICK * mobility * reverseDirection(keys, setup) * speedFactor;
        if (keys.leftOnly()) {
            return yaw + turn;
        }
        return keys.rightOnly() ? yaw - turn : yaw;
    }

    /**
     * Terrain following alone, for a tank nobody drives: the server settles it onto the slope at
     * once per tick. Off unless the server enables it, since parked tanks never used to settle.
     */
    public static Attitude followTerrainUnmanned(Attitude attitude, Setup setup, AttitudeTerrain terrain,
                                                 float dtSeconds) {
        Attitude followed = followTerrain(attitude, setup, onSurface(setup, terrain), dtSeconds);
        return followed.withRoll(StickIntegration.wrapRoll(followed.roll()));
    }

    private static Attitude settle(Attitude attitude, ControlInput keys, Setup setup, AttitudeContext context,
                                   float dtSeconds) {
        if (context.wrecked()) {
            return attitude;
        }
        Attitude settled = setup.gunnerMode() ? gunnerAutopilot(attitude, setup, dtSeconds) : attitude;
        settled = setup.recoil().apply(settled, dtSeconds);
        AttitudeTerrain terrain = context.terrain();
        boolean onSurface = onSurface(setup, terrain);
        settled = followTerrain(settled, setup, onSurface, dtSeconds);
        float mobility = setup.groundTurning().factor(terrain.airborne(), terrain.aboveWaterOrAir());
        float yaw = setup.car() && onSurface ? steerCar(settled.yaw(), keys, setup, mobility, dtSeconds)
                : steerTracks(settled.yaw(), keys, setup, mobility, dtSeconds);
        return settled.withYaw(yaw);
    }

    private static boolean onSurface(Setup setup, AttitudeTerrain terrain) {
        return !terrain.airborne() || setup.floats() && terrain.afloat();
    }

    private static Attitude gunnerAutopilot(Attitude attitude, Setup setup, float dtSeconds) {
        float keep = PerTick.decay(GUNNER_LEVELLING_PER_TICK, dtSeconds);
        float roll = attitude.roll();
        if (Math.abs(roll) > GUNNER_ROLL_LIMIT) {
            roll *= keep;
        }
        float turn = setup.autoPilotRot() * GUNNER_TURN_PER_AUTOPILOT * dtSeconds * PerTick.TICKS_PER_SECOND;
        return new Attitude(attitude.yaw() + turn, attitude.pitch() * keep, roll);
    }

    /** Eases pitch and roll towards the slope, quickly on the ground and slowly in the air. */
    private static Attitude followTerrain(Attitude attitude, Setup setup, boolean onSurface, float dtSeconds) {
        boolean moving = setup.motion().velocity().horizontalLength() > MOVING_SPEED;
        float pitch = follow(attitude.pitch(), setup.slope().pitch(), PITCH_FOLLOW, onSurface, moving, dtSeconds);
        float roll = follow(attitude.roll(), setup.slope().roll(), ROLL_FOLLOW, onSurface, moving, dtSeconds);
        return new Attitude(attitude.yaw(), pitch, roll);
    }

    private static float follow(float angle, float target, Follow rates, boolean onSurface, boolean moving,
                                float dtSeconds) {
        float share = onSurface ? Math.min(1.0F, dtSeconds * (moving ? rates.movingRate() : rates.stillRate()))
                : Math.min(1.0F, dtSeconds * rates.airborneRate());
        float desired = angle + (target - angle) * share;
        float maxStep = (onSurface ? GROUND_FOLLOW_MAX_RATE : rates.airborneMaxRate()) * dtSeconds;
        return angle + LegacyMath.clamp(desired - angle, -maxStep, maxStep);
    }

    /** Cars turn only while rolling, faster at speed, and mirrored when reversing. */
    private static float steerCar(float yaw, ControlInput keys, Setup setup, float mobility, float dtSeconds) {
        float steer = keys.steering();
        if (steer == 0.0F) {
            return yaw;
        }
        double speed = GroundGrip.signedForwardSpeed(setup.motion().velocity(), yaw);
        if (Math.abs(speed) < CAR_CREEP_SPEED) {
            speed = (keys.throttleUp() ? CAR_CREEP_SPEED : 0.0) - (keys.throttleDown() ? CAR_CREEP_SPEED : 0.0);
        }
        if (!(Math.abs(speed) > CAR_STOPPED_SPEED)) {
            return yaw;
        }
        float rate = (float) Math.min(CAR_MAX_STEER_RATE,
                CAR_MIN_STEER_RATE + Math.abs(speed) * CAR_STEER_RATE_PER_SPEED);
        return yaw + steer * rate * dtSeconds * (speed >= 0.0 ? 1.0F : -1.0F) * mobility;
    }

    /** Tracks pivot on the spot once the engine has some throttle; reversing slowly mirrors the keys. */
    private static float steerTracks(float yaw, ControlInput keys, Setup setup, float mobility, float dtSeconds) {
        Motion motion = setup.motion();
        float pivotThrottle = setup.pivotTurnThrottle();
        boolean canTurn = pivotThrottle <= 0.0F || motion.throttle() >= pivotThrottle
                || motion.reverseThrust() >= pivotThrottle / REVERSE_THRUST_PIVOT_SHARE
                || motion.movedSquared() > motion.reverseThrust() * REVERSE_MOVE_PER_THRUST;
        float steer = keys.steering();
        if (!canTurn || steer == 0.0F) {
            return yaw;
        }
        float speedFactor = pivotThrottle <= 0.0F ? 1.0F : (float) Math.sqrt(Math.min(motion.movedSquared(), 1.0));
        return yaw + steer * TRACK_YAW_RATE * mobility * dtSeconds * reverseDirection(keys, setup) * speedFactor;
    }

    private static float reverseDirection(ControlInput keys, Setup setup) {
        boolean reversing = !keys.throttleUp() && keys.throttleDown()
                && setup.motion().throttle() < setup.pivotTurnThrottle() + REVERSE_THROTTLE_MARGIN;
        return reversing ? -1.0F : 1.0F;
    }

    private static float withinRightAngle(float angle) {
        return LegacyMath.limit(angle, -RIGHT_ANGLE, RIGHT_ANGLE);
    }
}
