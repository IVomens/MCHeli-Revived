package com.ivomens.mcheli.core.guidance;

import org.joml.Vector3f;

/**
 * The geometry that makes a seeker lose its target: the target leaving the missile's field of view, the
 * target's motion leaving the doppler gate, and a plane notching a pulse doppler radar by flying across
 * its line of sight. Angles are compared in float, with JOML, as the game always did.
 */
public final class Seeker {

    /** A velocity shorter than this has no direction to notch with. */
    private static final float MIN_NOTCH_SPEED = 0.001f;

    private Seeker() {
    }

    /** Whether the target lies more than {@code maxDegrees} off the missile's flight direction. */
    public static boolean outsideFieldOfView(Vector3f flight, Vector3f toTarget, double maxDegrees) {
        return flight.angle(toTarget) > Math.toRadians(maxDegrees);
    }

    /**
     * Whether the target moves more than {@code maxDegrees} off the missile's flight direction, outside
     * the doppler gate of a pulse doppler seeker.
     */
    public static boolean outsideDopplerGate(Vector3f flight, Vector3f targetMotion, double maxDegrees) {
        return flight.angle(targetMotion) > Math.toRadians(maxDegrees);
    }

    /**
     * Whether a target flies across the shooter's course more steeply than {@code notchDegrees}, where a
     * pulse doppler radar loses it in the ground clutter. Head-on and tail-on count the same.
     */
    public static boolean isNotching(Vector3f shooterVelocity, Vector3f targetVelocity, float notchDegrees) {
        if (shooterVelocity.length() <= MIN_NOTCH_SPEED || targetVelocity.length() <= MIN_NOTCH_SPEED) {
            return false;
        }
        float cos = shooterVelocity.dot(targetVelocity) / (shooterVelocity.length() * targetVelocity.length());
        float crossing = (float) Math.acos(Math.clamp(cos, -1f, 1f));
        if (crossing > Math.PI / 2) crossing = (float) (Math.PI - crossing);
        return Math.toDegrees(crossing) > notchDegrees;
    }
}
