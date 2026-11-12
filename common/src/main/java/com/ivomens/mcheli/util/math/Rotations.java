package com.ivomens.mcheli.util.math;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rotations by yaw, pitch and roll in degrees, as vectors and as quaternions. Two conventions meet
 * here: the legacy one of {@link #rotate} (roll about Z, then pitch about X, then yaw about Y) and the
 * world orientation the renderer and the bounding boxes use ({@link #worldOrientation}).
 */
public final class Rotations {

    private Rotations() {
    }

    /** The unit vector a yaw and a pitch look along. */
    public static Vec3 direction(float yaw, float pitch) {
        return new Vec3(
                -Mth.sin(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI),
                -Mth.sin(pitch / 180.0F * (float) Math.PI),
                Mth.cos(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI));
    }

    public static Vec3 rotate(double x, double y, double z, float yaw, float pitch) {
        return rotate(new Vec3(x, y, z), yaw, pitch);
    }

    public static Vec3 rotate(double x, double y, double z, float yaw, float pitch, float roll) {
        return rotate(new Vec3(x, y, z), yaw, pitch, roll);
    }

    /** Rotates by pitch around X, then by yaw around Y. */
    public static Vec3 rotate(Vec3 vector, float yaw, float pitch) {
        return vector.xRot(pitch / 180.0F * (float) Math.PI).yRot(yaw / 180.0F * (float) Math.PI);
    }

    /** Rotates by roll around Z, pitch around X, then yaw around Y. */
    public static Vec3 rotate(Vec3 vector, float yaw, float pitch, float roll) {
        return vector.zRot(roll / 180.0F * (float) Math.PI)
                .xRot(pitch / 180.0F * (float) Math.PI)
                .yRot(yaw / 180.0F * (float) Math.PI);
    }

    /** Rotates (x, y) pairs in place. */
    public static void rotatePoints(double[] points, float degrees) {
        float radians = degrees / 180.0F * (float) Math.PI;
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);
        for (int i = 0; i + 1 < points.length; i += 2) {
            double x = points[i];
            double y = points[i + 1];
            points[i] = x * cos - y * sin;
            points[i + 1] = x * sin + y * cos;
        }
    }

    /** The orientation that turns vectors like {@link #rotate(Vec3, float, float, float)}. */
    public static Quaternionf orientation(float yaw, float pitch, float roll) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(-pitch))
                .rotateZ((float) Math.toRadians(-roll));
    }

    public static Vec3 transform(Quaternionf rotation, double x, double y, double z) {
        Vector3f v = rotation.transform(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3(v.x, v.y, v.z);
    }

    /**
     * Spherical interpolation between two orientations, returned as {yaw, pitch, roll}. It takes the
     * shortest path and has none of the wrap-around artefacts of interpolating each angle. The
     * decomposition is ambiguous at a pitch of +/-90, which is acceptable for one rendered frame.
     */
    public static float[] slerpYawPitchRoll(float yaw0, float pitch0, float roll0,
                                            float yaw1, float pitch1, float roll1, float t) {
        Quaternionf q = orientation(yaw0, pitch0, roll0);
        q.slerp(orientation(yaw1, pitch1, roll1), t);
        Vector3f e = q.getEulerAnglesYXZ(new Vector3f());
        return new float[] {
                (float) Math.toDegrees(e.y),
                (float) -Math.toDegrees(e.x),
                (float) -Math.toDegrees(e.z)
        };
    }

    /**
     * {pitch, yaw, roll} of an orientation built in Z, X, Y order (Rz(roll) * Rx(pitch) * Ry(yaw)). It
     * matches the old matrix based attitude code exactly, including its fallback near a pitch of +/-90,
     * so flight behaviour does not change.
     */
    public static float[] legacyEuler(Quaternionf q) {
        float x = q.x, y = q.y, z = q.z, w = q.w;
        // Rotation matrix elements, row major.
        float x2 = 2.0F * x * x, y2 = 2.0F * y * y, z2 = 2.0F * z * z;
        float xy = 2.0F * x * y, yz = 2.0F * y * z, zx = 2.0F * z * x;
        float wx = 2.0F * w * x, wy = 2.0F * w * y, wz = 2.0F * w * z;
        float m00 = 1.0F - y2 - z2;
        float m01 = xy - wz;
        float m02 = zx + wy;
        float m11 = 1.0F - z2 - x2;
        float m20 = zx - wy;
        float m21 = yz + wx;
        float m22 = 1.0F - x2 - y2;
        float b = (float) -Math.asin(m21);
        float cosB = (float) Math.cos(b);
        float a, c;
        if (Math.abs(cosB) >= 1.0e-4F) {
            c = (float) Math.atan2(m20, m22);
            float xyc = m01 / cosB;
            if (xyc > 1.0F) xyc = 1.0F; else if (xyc < -1.0F) xyc = -1.0F;
            a = (float) Math.asin(xyc);
            if (Float.isNaN(a)) a = 0.0F;
        } else {
            c = (float) Math.atan2(-m02, m00);
            a = 0.0F;
        }
        a = (float) Math.toDegrees(a);
        b = (float) Math.toDegrees(b);
        c = (float) Math.toDegrees(c);
        if (m11 < 0.0F) a = 180.0F - a;
        return new float[] { -b, -c, -a };
    }

    /**
     * The world orientation of the renderer and the bounding boxes: Ry(-yaw) * Rx(pitch) * Rz(roll), the
     * rotation the model transform applies. A body-local vector v is at {@code q.transform(v)} in the
     * world. {@link #worldEuler} is its inverse away from a pitch of +/-90.
     */
    public static Quaternionf worldOrientation(float yaw, float pitch, float roll) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));
    }

    /** {pitch, yaw, roll} of a {@link #worldOrientation}; ambiguous only at a pitch of +/-90. */
    public static float[] worldEuler(Quaternionf q) {
        Vector3f e = q.getEulerAnglesYXZ(new Vector3f());
        return new float[] {
                (float) Math.toDegrees(e.x),
                (float) -Math.toDegrees(e.y),
                (float) Math.toDegrees(e.z)
        };
    }

    /**
     * The world {yaw, pitch} of a barrel turned by {@code relYaw}, {@code relPitch} on an airframe. The
     * two attitudes are composed as quaternions, so the barrel pitches within the airframe's tilted
     * plane and the shot is right at any roll. Summing the angles, as the legacy code did, is only right
     * without roll; this gives the same result whenever the roll is zero.
     */
    public static float[] worldAim(float airYaw, float airPitch, float airRoll, float relYaw, float relPitch) {
        Quaternionf q = worldOrientation(airYaw, airPitch, airRoll).mul(worldOrientation(relYaw, relPitch, 0.0F));
        Vector3f d = q.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        double dy = d.y < -1.0 ? -1.0 : (d.y > 1.0 ? 1.0 : d.y);
        float pitch = (float) -Math.toDegrees(Math.asin(dy));
        return new float[] { yaw, pitch };
    }
}
