package com.ivomens.mcheli.api.wingman;

/**
 * Whether and how a wingman attacks.
 *
 * <p>Immutable; any thread. Since API 1.1. A minor version may add constants, so a {@code switch} over this
 * enum needs a {@code default} branch.
 */
public enum WingmanAttack {
    /** Holding fire. */
    HOLD_FIRE,
    /** Attacking the target it was given ({@link WingmanState#attackTarget()}), or its leader's lock without one. */
    TARGET,
    /** Picking the nearest hostile itself. */
    AUTO
}
