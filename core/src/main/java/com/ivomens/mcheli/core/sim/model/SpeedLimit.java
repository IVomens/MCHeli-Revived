package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.math.Vec3d;

/** Caps the speed over the ground; vertical speed is never capped. */
public final class SpeedLimit {

    private SpeedLimit() {
    }

    /**
     * @param velocity the velocity, scaled down when it was too fast
     * @param speed    the horizontal speed after the cap; exactly the limit when capped
     */
    public record Limited(Vec3d velocity, double speed) {
    }

    public static Limited clamp(Vec3d velocity, float limit) {
        double speed = velocity.horizontalLength();
        if (!(speed > limit)) {
            return new Limited(velocity, speed);
        }
        double scale = limit / speed;
        return new Limited(new Vec3d(velocity.x() * scale, velocity.y(), velocity.z() * scale), limit);
    }
}
