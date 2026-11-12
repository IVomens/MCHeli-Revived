package com.ivomens.mcheli.core.wingman.brain;

import java.util.Locale;

/**
 * How the wingman autopilot employs a weapon category: attack geometry, firing range and rate. Weapon
 * types are the free-form {@code type} strings of MCHeli weapon definitions (and the wingman's selected
 * weapon filter); unknown types get {@link #OTHER}.
 */
public enum WeaponProfile {
    //        standoff  altitude  range  cooldown  fixed  heavy  orbit
    MACHINE_GUN(50,     40,       120,   2,        true,  false, false),
    CANNON(     50,     40,       120,   5,        true,  false, false),
    CAS(        60,     50,       140,   3,        true,  false, false),
    ROCKET(     80,     60,       200,   10,       true,  false, false),
    MISSILE(    200,    80,       300,   20,       false, true,  true),
    TV_MISSILE( 200,    80,       300,   30,       false, true,  true),
    AS_MISSILE( 280,    200,      380,   60,       false, true,  true),
    BOMB(       180,    200,      350,   80,       false, true,  false),
    TORPEDO(    40,     5,        80,    30,       false, false, false),
    OTHER(      80,     50,       150,   10,       false, false, false);

    /** Horizontal distance to keep from the target while attacking. */
    public final double standoff;
    /** Attack altitude above the target. */
    public final double altitudeOffset;
    /** Weapons are fired once the target is this close. */
    public final double fireRange;
    /** Ticks between shots. */
    public final int cooldownTicks;
    /** Fixed forward-firing weapon: the nose has to point at the target. */
    public final boolean fixedForward;
    /** After firing, fly straight for a moment to clear the blast or launch. */
    public final boolean heavy;
    /** Guided weapon that can be fired from any direction, so the aircraft orbits instead of diving in. */
    public final boolean orbitFire;

    WeaponProfile(double standoff, double altitudeOffset, double fireRange, int cooldownTicks,
                  boolean fixedForward, boolean heavy, boolean orbitFire) {
        this.standoff = standoff;
        this.altitudeOffset = altitudeOffset;
        this.fireRange = fireRange;
        this.cooldownTicks = cooldownTicks;
        this.fixedForward = fixedForward;
        this.heavy = heavy;
        this.orbitFire = orbitFire;
    }

    public static WeaponProfile of(String type) {
        if (type == null) return OTHER;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "gun", "machinegun", "machinegun1", "machinegun2" -> MACHINE_GUN;
            case "cannon" -> CANNON;
            case "cas" -> CAS;
            case "rocket", "mkrocket" -> ROCKET;
            case "missile", "aamissile", "atmissile" -> MISSILE;
            case "tvmissile" -> TV_MISSILE;
            case "asmissile" -> AS_MISSILE;
            case "bomb" -> BOMB;
            case "torpedo" -> TORPEDO;
            default -> OTHER;
        };
    }
}
