package com.ivomens.mcheli.combat.weapon;

/** What pulling the trigger of a {@link WeaponGroup} did. */
public enum FireOutcome {

    NOT_FIRED,
    FIRED,
    /** Fired the last round of the magazine; the group reloads now. */
    FIRED_AND_RELOADING;

    public boolean fired() {
        return this != NOT_FIRED;
    }

    public boolean reloading() {
        return this == FIRED_AND_RELOADING;
    }
}
