package com.ivomens.mcheli.api.event;

/**
 * What the missiles of a {@link MissileWarned} seek with, as the vehicle's warning tells the crew: whether
 * flares, chaff or neither will help. Since API 1.1: constants are never removed within a major version, but a
 * minor one may add some, so a {@code switch} over this enum needs a {@code default} branch.
 */
public enum SeekerType {
    /** Infrared seekers, which flares decoy. */
    HEAT,
    /** Radar seekers, which chaff decoys. */
    RADAR,
    /** Both kinds: a dual seeker, or heat and radar missiles at once; flares and chaff each help. */
    DUAL,
    /** Missiles no decoy fools, or a seeker MCHeli does not know (another mod's missile). */
    OTHER
}
