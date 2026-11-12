package com.ivomens.mcheli.entityview;

/**
 * A mob that crews a vehicle's seat in place of a player: it fires the seat's weapons, counts for team
 * checks like a player does, and reloads more slowly than a player.
 */
public interface AiGunner {

    /** Whether it was placed in creative mode, which spares its vehicle ammunition and damage. */
    boolean isCreative();
}
