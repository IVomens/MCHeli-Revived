package com.ivomens.mcheli.api.target;

/**
 * What a seeker locks with, which says which decoy fools it: flares fool heat seekers, chaff fools radar.
 *
 * <p>Immutable; any thread, either side. Since API 1.1. A minor version may add constants, so a {@code switch}
 * over this enum needs a {@code default} branch.
 */
public enum SeekerType {
    /** An infrared seeker; flares decoy it. */
    HEAT,
    /** A radar; chaff decoys it. */
    RADAR,
    /** Infrared and radar together; flares and chaff each decoy it. */
    DUAL,
    /** Anything else, such as an optical or laser seeker; no decoy fools it. */
    OTHER
}
