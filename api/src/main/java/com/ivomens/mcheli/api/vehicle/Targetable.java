package com.ivomens.mcheli.api.vehicle;

/**
 * How a vehicle shows up to seekers and incoming fire: its stealth and the countermeasures running right
 * now. Guided weapons read it when they look for, hold or break a lock; it never changes the vehicle.
 *
 * <p>Both sides, on the thread of the vehicle's level. Stable since API 1.0; implemented by MCHeli only, so a
 * minor version may add methods.
 */
public interface Targetable {

    /** How hard the vehicle is to lock: 0 for not at all stealthy, up to 1. */
    float stealth();

    /** Whether flares are burning, which heat seekers follow instead. */
    boolean isFlareUsing();

    /** Ticks the chaff cloud the crew announced keeps blinding radar seekers; 0 when there is none. */
    int announcedChaffTime();

    /** Whether a jammer that breaks radar locks is running. */
    boolean isBreakingRadarLocks();

    /** Whether the iron curtain is running, which stops incoming projectiles. */
    boolean isIronCurtainRunning();
}
