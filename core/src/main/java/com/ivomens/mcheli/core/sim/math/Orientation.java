package com.ivomens.mcheli.core.sim.math;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An attitude held as a unit quaternion in the render convention, {@code Ry(-yaw) * Rx(pitch) * Rz(roll)}:
 * the rotation the renderer gives the model, so a camera that follows it agrees with the model at
 * any attitude. Unlike yaw, pitch and roll it has no gimbal lock, which is why the quaternion
 * attitude mode keeps one from step to step and only reads the angles back out of it.
 */
public record Orientation(float x, float y, float z, float w) {

    public static Orientation of(Attitude attitude) {
        return of(attitude.yaw(), attitude.pitch(), attitude.roll());
    }

    public static Orientation of(float yaw, float pitch, float roll) {
        return from(new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll)));
    }

    public static Orientation from(Quaternionf quaternion) {
        return new Orientation(quaternion.x, quaternion.y, quaternion.z, quaternion.w);
    }

    /** This orientation turned by {@code turn} in its own (body) frame, then renormalised. */
    public Orientation turnedBy(Orientation turn) {
        return from(toQuaternion().mul(turn.toQuaternion()).normalize());
    }

    /** Back to yaw, pitch and roll; ambiguous only at exactly +/-90 degrees pitch. */
    public Attitude toAttitude() {
        Vector3f angles = toQuaternion().getEulerAnglesYXZ(new Vector3f());
        return new Attitude((float) -Math.toDegrees(angles.y), (float) Math.toDegrees(angles.x),
                (float) Math.toDegrees(angles.z));
    }

    /** The orientation {@code fraction} of the way to {@code target}, along the shorter arc. */
    public Orientation towards(Orientation target, float fraction) {
        return from(toQuaternion().slerp(target.toQuaternion(), fraction));
    }

    /** The smallest rotation that turns this orientation into {@code other}, in degrees. */
    public float degreesTo(Orientation other) {
        float dot = Math.abs(this.x * other.x + this.y * other.y + this.z * other.z + this.w * other.w);
        return (float) Math.toDegrees(2.0 * Math.acos(Math.min(dot, 1.0F)));
    }

    /** A fresh JOML copy, for code that composes it with other rotations. */
    public Quaternionf toQuaternion() {
        return new Quaternionf(this.x, this.y, this.z, this.w);
    }
}
