package com.ivomens.mcheli.api.event;

/**
 * The countermeasures a vehicle can carry. Stable since API 1.0: constants are never removed within a major
 * version, but a minor one may add some, so a {@code switch} over this enum needs a {@code default} branch.
 */
public enum CountermeasureType {
    /** Flares, which heat seekers follow instead of the vehicle. */
    FLARE,
    /** A chaff cloud, which blinds radar seekers. */
    CHAFF,
    /** An active protection system, which shoots down incoming missiles. */
    ACTIVE_PROTECTION,
    /** The iron curtain, which stops incoming projectiles. */
    IRON_CURTAIN,
    /** An ECM jammer, which breaks radar locks. */
    ECM_JAMMER
}
