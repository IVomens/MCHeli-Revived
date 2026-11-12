package com.ivomens.mcheli.core.wingman.brain;

/**
 * A position or a motion in world coordinates. The distance methods use the same operations as
 * Minecraft's {@code Vec3}, so results match the game's to the last bit.
 */
public record Point(double x, double y, double z) {

    /** Distance to {@code other}, as {@code Vec3.distanceTo}. */
    public double distanceTo(Point other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Horizontal distance from here to the point (x, z). */
    public double horizontalDistanceTo(double x, double z) {
        double dx = x - this.x;
        double dz = z - this.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Length of this motion, as {@code Vec3.length}. */
    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /** Horizontal length of this motion, as {@code Vec3.horizontalDistance}. */
    public double horizontalLength() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }
}
