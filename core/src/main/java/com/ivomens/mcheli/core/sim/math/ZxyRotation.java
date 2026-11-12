package com.ivomens.mcheli.core.sim.math;

import org.joml.Quaternionf;

/**
 * The Euler attitude maths, kept bit for bit: rotations built in Z, X, Y order
 * ({@code Rz(roll) * Rx(pitch) * Ry(yaw)}, the order of the original matrix code) and read back into
 * angles with that code's own fallback near +/-90 degrees pitch.
 * <p>
 * The order matters: composing the stick turn in front of the current attitude is what makes the
 * stick act in the pilot's frame, and handling depends on it, so it does not follow the render
 * convention of {@link Orientation}.
 */
public final class ZxyRotation {

    /** The legacy constant; it differs from {@link Math#toRadians} in the last bit. */
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;
    /** Below this the pitch counts as straight up or down, and roll is folded into yaw. */
    private static final float GIMBAL_COSINE = 1.0e-4F;

    private ZxyRotation() {
    }

    /** The turn applied first, then the current attitude: how the stick turns the airframe in Euler mode. */
    public static Attitude turn(Attitude current, float yaw, float pitch, float roll) {
        return angles(new Quaternionf()
                .rotateZ(roll * DEGREES_TO_RADIANS)
                .rotateX(pitch * DEGREES_TO_RADIANS)
                .rotateY(yaw * DEGREES_TO_RADIANS)
                .rotateZ(current.roll() * DEGREES_TO_RADIANS)
                .rotateX(current.pitch() * DEGREES_TO_RADIANS)
                .rotateY(current.yaw() * DEGREES_TO_RADIANS));
    }

    /**
     * The attitude rebuilt through the rotation, as tanks do every frame, which brings the angles
     * back into their usual ranges. Tanks convert to radians by division, so they keep that rounding.
     */
    public static Attitude rebuild(Attitude attitude) {
        return angles(new Quaternionf()
                .rotateZ(LegacyMath.floatRadians(attitude.roll()))
                .rotateX(LegacyMath.floatRadians(attitude.pitch()))
                .rotateY(LegacyMath.floatRadians(attitude.yaw())));
    }

    /** Reads the angles out of the rotation matrix of {@code q}. */
    private static Attitude angles(Quaternionf q) {
        float x2 = 2.0F * q.x * q.x;
        float y2 = 2.0F * q.y * q.y;
        float z2 = 2.0F * q.z * q.z;
        float xy = 2.0F * q.x * q.y;
        float yz = 2.0F * q.y * q.z;
        float zx = 2.0F * q.z * q.x;
        float wx = 2.0F * q.w * q.x;
        float wy = 2.0F * q.w * q.y;
        float wz = 2.0F * q.w * q.z;
        float m00 = 1.0F - y2 - z2;
        float m01 = xy - wz;
        float m02 = zx + wy;
        float m11 = 1.0F - z2 - x2;
        float m20 = zx - wy;
        float m21 = yz + wx;
        float m22 = 1.0F - x2 - y2;

        float pitchRad = (float) -Math.asin(m21);
        float cosPitch = (float) Math.cos(pitchRad);
        float yawRad;
        float rollRad;
        if (Math.abs(cosPitch) >= GIMBAL_COSINE) {
            yawRad = (float) Math.atan2(m20, m22);
            rollRad = (float) Math.asin(LegacyMath.clamp(m01 / cosPitch, -1.0F, 1.0F));
            if (Float.isNaN(rollRad)) {
                rollRad = 0.0F;
            }
        } else {
            yawRad = (float) Math.atan2(-m02, m00);
            rollRad = 0.0F;
        }
        float roll = (float) Math.toDegrees(rollRad);
        // upside down, the matrix reads as the mirrored roll
        if (m11 < 0.0F) {
            roll = 180.0F - roll;
        }
        return new Attitude(-(float) Math.toDegrees(yawRad), -(float) Math.toDegrees(pitchRad), -roll);
    }
}
