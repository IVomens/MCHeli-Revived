package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Marker;

/**
 * A runway between its RUNWAY_A threshold (where takeoffs start and landings touch down) and its
 * RUNWAY_B end, with the derived geometry the autopilot needs. Coordinates are block centres; the
 * height is the threshold block's.
 */
public record Runway(String startId, String endId, double ax, double ay, double az, double bx, double bz,
                     double length, double dirX, double dirZ) {

    public static Runway of(Marker start, Marker end) {
        double ax = start.centerX();
        double ay = start.blockY();
        double az = start.centerZ();
        double bx = end.centerX();
        double bz = end.centerZ();
        double dx = bx - ax;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        double dirX = length > 0 ? dx / length : 0;
        double dirZ = length > 0 ? dz / length : 0;
        return new Runway(start.id(), end.id(), ax, ay, az, bx, bz, length, dirX, dirZ);
    }

    /** Markers closer than a block apart give no usable direction. */
    boolean isUsable() {
        return this.length >= 1;
    }

    /** Yaw of the takeoff direction (A to B). */
    float yaw() {
        return Angles.yawTowards(this.dirX, this.dirZ);
    }

    /** Distance along the centreline, measured from A towards B. */
    double distanceFromStart(Point p) {
        return (p.x() - this.ax) * this.dirX + (p.z() - this.az) * this.dirZ;
    }

    double distanceToEnd(Point p) {
        return this.length - distanceFromStart(p);
    }
}
