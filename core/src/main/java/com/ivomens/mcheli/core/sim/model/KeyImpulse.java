package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Vec3d;

/**
 * A fixed horizontal push towards the held keys: helicopter strafing and taxiing, and how ground
 * vehicles drive. Throttle-up pushes forward, throttle-down back and the side keys sideways; the
 * directions are summed and the push has the same strength whatever the combination.
 */
public final class KeyImpulse {

    private KeyImpulse() {
    }

    /** The pushed velocity, or the same velocity when no key pushes. */
    public static Vec3d push(Vec3d velocity, float yaw, ControlInput keys, double strength) {
        double x = 0.0;
        double z = 0.0;
        boolean pushed = false;
        if (keys.throttleUp()) {
            x += sinDegrees(yaw);
            z += cosDegrees(yaw);
            pushed = true;
        }
        if (keys.throttleDown()) {
            x += sinDegrees(yaw - 180.0F);
            z += cosDegrees(yaw - 180.0F);
            pushed = true;
        }
        if (keys.leftOnly()) {
            x += sinDegrees(yaw - 90.0F);
            z += cosDegrees(yaw - 90.0F);
            pushed = true;
        }
        if (keys.rightOnly()) {
            x += sinDegrees(yaw + 90.0F);
            z += cosDegrees(yaw + 90.0F);
            pushed = true;
        }

        double length = Math.sqrt(x * x + z * z);
        if (!pushed || length <= 0.0) {
            return velocity;
        }
        // Minecraft yaw runs clockwise, hence the mirrored x
        return new Vec3d(velocity.x() - x / length * strength, velocity.y(), velocity.z() + z / length * strength);
    }

    private static double sinDegrees(float degrees) {
        return Math.sin(degrees * Math.PI / 180.0);
    }

    private static double cosDegrees(float degrees) {
        return Math.cos(degrees * Math.PI / 180.0);
    }
}
