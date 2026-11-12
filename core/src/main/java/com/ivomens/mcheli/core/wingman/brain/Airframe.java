package com.ivomens.mcheli.core.wingman.brain;

/**
 * What the brain knows about the aircraft it flies, beyond where it is and how it moves.
 *
 * @param kind         helicopter, plane, VTOL plane or another vehicle
 * @param playerAboard a player sits in the pilot seat; the autopilot then also turns the player's
 *                     view, since MCHeli copies the pilot's yaw into the aircraft
 * @param fuel         fuel left, negative for aircraft that never run dry
 * @param fuelCapacity tank size; zero or less for aircraft without a fuel budget
 * @param gravity      the definition's gravity in air, per tick (negative); a helicopter's collective
 *                     works against it
 * @param speedLimit   a helicopter's top speed, blocks per tick, which also scales its tilt thrust
 */
public record Airframe(Kind kind, boolean playerAboard, int fuel, int fuelCapacity, float gravity,
                       float speedLimit) {

    /** Gravity of a definition that does not set its own. */
    public static final float DEFAULT_GRAVITY = -0.04F;

    public enum Kind {
        HELICOPTER,
        /** A fixed-wing plane without a thrust-vectoring nozzle. */
        PLANE,
        /** A plane with a thrust-vectoring nozzle. */
        VTOL_PLANE,
        /** Any other vehicle (tank, ship, ground vehicle). */
        OTHER
    }

    public boolean isHelicopter() {
        return this.kind == Kind.HELICOPTER;
    }

    public boolean isVtolPlane() {
        return this.kind == Kind.VTOL_PLANE;
    }

    /** Helicopters and VTOL planes can operate from HELIPAD markers. */
    public boolean canUseHelipad() {
        return isHelicopter() || isVtolPlane();
    }
}
