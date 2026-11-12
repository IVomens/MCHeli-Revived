package com.ivomens.mcheli.core.wingman.mission;

import java.util.HashSet;
import java.util.Set;

/** Mission types. Several can be combined, except the exclusive ones. */
public enum MissionType {
    /** Combat air patrol: orbit and intercept air threats. */
    CAP("CAP", "aamissile", "gun"),
    /** Close air support: orbit and attack ground targets. */
    CAS("CAS", "cas", "rocket", "gun"),
    /** Strike: a fixed number of attack passes, then RTB. Exclusive. */
    STRIKE("Strike", "asmissile", "bomb", "gun"),
    /** Escort: stay with the player and intercept threats. */
    ESCORT("Escort", "aamissile", "gun"),
    /** Recon: fly once around the area, report the mob count, then RTB. */
    RECON("Recon", "gun"),
    /** Ferry: move to another base without fighting. Exclusive. */
    FERRY("Ferry");

    private final String displayName;
    private final String[] defaultWeapons;

    MissionType(String displayName, String... defaultWeapons) {
        this.displayName = displayName;
        this.defaultWeapons = defaultWeapons;
    }

    /** Default weapon set for this mission type (a fresh, mutable set). */
    public Set<String> defaultWeapons() {
        return new HashSet<>(Set.of(defaultWeapons));
    }

    public String displayName() {
        return displayName;
    }
}
