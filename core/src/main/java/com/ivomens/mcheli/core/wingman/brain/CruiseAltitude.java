package com.ivomens.mcheli.core.wingman.brain;

/**
 * The height an autonomous flight cruises at. Orders and node missions name it as an absolute Y (the
 * planner's "Cruise Y", 80 unless set, as in McHeli CE), which is what the aircraft flies over low
 * ground. The default is 16 blocks above sea level; over higher ground the aircraft keeps that much
 * clearance instead: above the runway or pad it departs from, and en route above the ground under it,
 * ahead of it and at its destination. Without the floor a base above Y 75 counted its climb as done on
 * the runway, and a flight over hills steered into them.
 */
final class CruiseAltitude {

    /** The least height above the ground a cruise keeps. */
    static final double MIN_CLEARANCE = 16.0;
    /** En route the ground is also checked this far ahead, towards the destination. */
    private static final double LOOKAHEAD = 48.0;

    private CruiseAltitude() {
    }

    /** The asked altitude, or {@link #MIN_CLEARANCE} above {@code groundY} if that is higher. */
    static double above(double asked, double groundY) {
        return Math.max(asked, groundY + MIN_CLEARANCE);
    }

    /** Cruise altitude on the way from {@code self} to ({@code toX}, {@code toZ}). */
    static double enroute(Perception p, Point self, double toX, double toZ, double asked) {
        double ground = ground(p, self.x(), self.z());
        double dx = toX - self.x();
        double dz = toZ - self.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.0) {
            double ahead = Math.min(LOOKAHEAD, dist) / dist;
            ground = Math.max(ground, ground(p, self.x() + dx * ahead, self.z() + dz * ahead));
        }
        ground = Math.max(ground, ground(p, toX, toZ));
        return above(asked, ground);
    }

    private static double ground(Perception p, double x, double z) {
        return p.senses().groundHeight((int) Math.floor(x), (int) Math.floor(z));
    }
}
