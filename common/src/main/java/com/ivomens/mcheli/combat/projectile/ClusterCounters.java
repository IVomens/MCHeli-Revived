package com.ivomens.mcheli.combat.projectile;

/** The countdowns a cluster weapon's projectile keeps: until it releases its bomblets, and children spread. */
final class ClusterCounters {

    /** Ticks until the bomblets are released; 0 when they never will be. */
    int releaseTimer;
    /** Salvos of sub-projectiles spread in flight so far. */
    int salvosSpread;
}
