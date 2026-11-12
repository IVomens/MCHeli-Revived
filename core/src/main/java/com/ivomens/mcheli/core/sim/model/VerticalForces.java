package com.ivomens.mcheli.core.sim.model;

/**
 * Gravity, lift and buoyancy along the vertical axis. All vehicle types share these laws; where
 * they used to differ, the difference is now an explicit parameter.
 */
public final class VerticalForces {

    /** Lift every fixed wing and tank gets for free. With gravity -0.04 it cancels out at full throttle. */
    private static final double BASE_LIFT = 0.04;
    /** How much faster the vehicle sinks per unit of missing throttle. */
    private static final double THROTTLE_SINK = 0.047;
    /** Gunner mode and target drones damp climbing and sinking instead of applying the law. */
    private static final double HOLD_ALTITUDE_DAMPING = 0.8;
    private static final double SHALLOW_SINK = 1.0E-4;
    private static final double FLOAT_LIFT = 0.007;

    /** Damping of a deep hull's sinking speed: helicopters keep more of it than hulls do. */
    public static final double ROTOR_SINK_DAMPING = 0.7;
    public static final double HULL_SINK_DAMPING = 0.5;

    private VerticalForces() {
    }

    /** Per-tick gravity from the definition, in air and in water. */
    public record Gravity(float air, float water) {

        public float in(boolean inWater) {
            return inWater ? this.water : this.air;
        }
    }

    public static double gravity(double motionY, boolean inWater, Gravity gravity) {
        return motionY + gravity.in(inWater);
    }

    /**
     * The fixed-wing vertical law {@code 0.04 + g - 0.047 (1 - throttle)}: there is no aerodynamic
     * lift, the vehicle simply sinks faster the lower its throttle.
     */
    public static double poweredSink(double motionY, boolean inWater, Gravity gravity, double throttle) {
        double lift = BASE_LIFT + gravity.in(inWater);
        return motionY + lift - THROTTLE_SINK * (1.0 - throttle);
    }

    /** Planes and tanks off the water: the vertical law, or altitude hold. */
    public static double unsupported(double motionY, boolean holdAltitude, boolean inWater, Gravity gravity,
                                     double throttle) {
        if (holdAltitude) {
            return motionY * HOLD_ALTITUDE_DAMPING;
        }
        return poweredSink(motionY, inWater, gravity, throttle);
    }

    /** A floating hull: shallow water barely holds it up, deep water pushes it up. */
    public static double floating(double motionY, double waterDepth, double throttle, double sinkDamping) {
        return waterDepth < 1.0 ? shallowWater(motionY, throttle) : deepWater(motionY, sinkDamping);
    }

    /** Partly submerged: a slight sink, offset by throttle. */
    public static double shallowWater(double motionY, double throttle) {
        return motionY - SHALLOW_SINK + FLOAT_LIFT * throttle;
    }

    /** Fully submerged: sinking is damped, then the hull is pushed up. */
    public static double deepWater(double motionY, double sinkDamping) {
        double damped = motionY < 0.0 ? motionY * sinkDamping : motionY;
        return damped + FLOAT_LIFT;
    }
}
