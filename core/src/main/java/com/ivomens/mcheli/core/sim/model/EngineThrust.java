package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.math.Vec3d;

/** Engine thrust of planes, ships and tanks. */
public final class EngineThrust {

    /** Full throttle adds this much speed per tick along the thrust direction. */
    private static final double FULL_THROTTLE_PER_TICK = 10.0;

    private EngineThrust() {
    }

    /** Thrust per tick for a throttle setting, rounded to float as it always was. */
    public static float perTick(double throttle) {
        return (float) (throttle / FULL_THROTTLE_PER_TICK);
    }

    /**
     * Pushes along the horizontal part of {@code direction}, or backwards with the reverse lever
     * when the definition has reverse and the lever is pulled.
     */
    public static Vec3d pushHorizontally(Vec3d velocity, Vec3d direction, float thrust, float reverseThrust,
                                         boolean reverseEnabled) {
        if (reverseEnabled && reverseThrust > 0.0F) {
            return new Vec3d(velocity.x() - direction.x() * reverseThrust, velocity.y(),
                    velocity.z() - direction.z() * reverseThrust);
        }
        return new Vec3d(velocity.x() + direction.x() * thrust, velocity.y(), velocity.z() + direction.z() * thrust);
    }
}
