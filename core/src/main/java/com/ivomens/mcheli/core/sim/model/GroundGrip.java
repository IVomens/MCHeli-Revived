package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.core.sim.math.Vec3d;

/**
 * How wheels and tracks grip the ground: the horizontal velocity is split into a rolling part along
 * the hull and a sliding part across it, and each is damped by its own factor.
 */
public final class GroundGrip {

    private GroundGrip() {
    }

    /**
     * Factors that keep part of the velocity each tick.
     *
     * @param lateral how much sideways slide survives; small means firm grip
     * @param rolling how much rolling speed survives
     */
    public record Grip(double lateral, double rolling) {
    }

    public static Vec3d apply(Vec3d velocity, float yaw, Grip grip) {
        Vec3d forward = LegacyMath.flatForward(yaw);
        double forwardSpeed = (velocity.x() * forward.x() + velocity.z() * forward.z()) * grip.rolling();
        double lateralSpeed = (velocity.x() * -forward.z() + velocity.z() * forward.x()) * grip.lateral();
        return new Vec3d(forward.x() * forwardSpeed - forward.z() * lateralSpeed, velocity.y(),
                forward.z() * forwardSpeed + forward.x() * lateralSpeed);
    }

    /** Speed along the nose; negative when reversing. */
    public static double signedForwardSpeed(Vec3d velocity, float yaw) {
        Vec3d forward = LegacyMath.flatForward(yaw);
        return velocity.x() * forward.x() + velocity.z() * forward.z();
    }
}
