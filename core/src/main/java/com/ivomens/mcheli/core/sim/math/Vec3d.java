package com.ivomens.mcheli.core.sim.math;

/** An immutable vector of doubles: a velocity, a displacement or a direction. */
public record Vec3d(double x, double y, double z) {

    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /** Speed over the ground, ignoring the vertical part. */
    public double horizontalLength() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public Vec3d withY(double newY) {
        return new Vec3d(this.x, newY, this.z);
    }

    /** Scales the horizontal part only; climbing and sinking are damped separately. */
    public Vec3d scaleHorizontal(double factor) {
        return new Vec3d(this.x * factor, this.y, this.z * factor);
    }

    public Vec3d scale(double factorX, double factorY, double factorZ) {
        return new Vec3d(this.x * factorX, this.y * factorY, this.z * factorZ);
    }

    /** This vector with length one, or unchanged when it has no length. */
    public Vec3d normalized() {
        double length = length();
        return length != 0.0 ? new Vec3d(this.x / length, this.y / length, this.z / length) : this;
    }
}
