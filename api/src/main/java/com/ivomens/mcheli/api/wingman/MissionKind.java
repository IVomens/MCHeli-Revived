package com.ivomens.mcheli.api.wingman;

/**
 * The kinds of mission order a wingman flies, as the wingman's mission planner offers them. Several can be
 * combined in one order; the planner offers {@link #STRIKE} and {@link #FERRY} on their own.
 *
 * <p>Immutable; any thread. Since API 1.1. A minor version may add constants, so a {@code switch} over this
 * enum needs a {@code default} branch.
 */
public enum MissionKind {
    /** Combat air patrol: orbit the target point and intercept air threats. */
    CAP,
    /** Close air support: orbit the target point and attack ground targets. */
    CAS,
    /** A fixed number of attack passes on the target point, then back to base. */
    STRIKE,
    /** Stay with the player who gave the order and intercept threats. */
    ESCORT,
    /** Fly once around the target point, report what is there, then back to base. */
    RECON,
    /**
     * Fly to the target point without fighting, wait there briefly, then fly back to the base and land. Ferry
     * to a point next to the base to send a wingman home.
     */
    FERRY
}
