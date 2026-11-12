package com.ivomens.mcheli.content.def.weapon;

/**
 * Fuze/trigger type for a bomb dropped in "mine mode".
 *
 * <p>A bomb whose weapon definition declares any fuze other than {@link #NONE} is saved with the
 * world: instead of detonating on impact it settles on the ground, arms after a short delay, and
 * then detonates when its trigger comes within range.
 */
public enum MineFuze {
    /** Not a mine: ordinary bomb behaviour. */
    NONE,
    /** Detonates when a (non-sneaking) player enters the proximity range. */
    PROXIMITY_PLAYER,
    /** Detonates when an MCHeli vehicle/aircraft enters the proximity range. */
    PROXIMITY_VEHICLE
}
