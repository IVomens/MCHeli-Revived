package com.ivomens.mcheli.config;

/**
 * Handling that applies to every vehicle: speed multipliers, how attitude is integrated, and the
 * server's helicopter tilt limit. The physics reads these through {@code SimSettings}, never directly.
 */
public record FlightSettings(double heliSpeed, double planeSpeed, double shipSpeed, double tankSpeed,
                             boolean quaternionRotation, boolean fixedStepAttitude, boolean serverTerrainFollow,
                             boolean enableRotationLimit, int pitchLimitMax, int pitchLimitMin, int rollLimit) {

    private static final double MAX_SPEED_MULTIPLIER = 1000.0;
    private static final int MAX_TILT = 80;

    public static final DoubleOption HELI_SPEED = speed("AllHeliSpeed");
    public static final DoubleOption PLANE_SPEED = speed("AllPlaneSpeed");
    public static final DoubleOption SHIP_SPEED = speed("AllShipSpeed");
    public static final DoubleOption TANK_SPEED = speed("AllTankSpeed");
    public static final BoolOption QUATERNION_ROTATION = BoolOption.of("ExperimentalQuaternionRotation", true);
    /** Client: attitude at fixed 1/60 s substeps; off is the old integration once per render frame. */
    public static final BoolOption FIXED_STEP_ATTITUDE = BoolOption.of("FixedStepAttitude", true)
            .describe("FixedStepAttitude = true turns the aircraft in fixed 1/60 s steps, so handling does not"
                    + " depend on the frame rate. false = the old per-frame turning.");
    /** Server: tanks nobody drives settle onto the slope below them. */
    public static final BoolOption SERVER_TERRAIN_FOLLOW = BoolOption.of("ServerTerrainFollow", false)
            .describe("ServerTerrainFollow = true lets the server settle tanks nobody drives onto the slope below"
                    + " them. false (default) = they keep the attitude they were left at.");
    public static final BoolOption ENABLE_ROTATION_LIMIT = BoolOption.of("EnableRotationLimit", false);
    public static final IntOption PITCH_LIMIT_MAX = IntOption.of("PitchLimitMax", 10).range(0, MAX_TILT);
    public static final IntOption PITCH_LIMIT_MIN = IntOption.of("PitchLimitMin", -10).range(-MAX_TILT, 0);
    public static final IntOption ROLL_LIMIT = IntOption.of("RollLimit", 35).range(0, MAX_TILT);

    static FlightSettings from(ConfigValues v) {
        return new FlightSettings(v.get(HELI_SPEED), v.get(PLANE_SPEED), v.get(SHIP_SPEED), v.get(TANK_SPEED),
                v.get(QUATERNION_ROTATION), v.get(FIXED_STEP_ATTITUDE), v.get(SERVER_TERRAIN_FOLLOW),
                v.get(ENABLE_ROTATION_LIMIT), v.get(PITCH_LIMIT_MAX), v.get(PITCH_LIMIT_MIN), v.get(ROLL_LIMIT));
    }

    private static DoubleOption speed(String key) {
        return DoubleOption.of(key, 1.0).range(0.0, MAX_SPEED_MULTIPLIER);
    }
}
