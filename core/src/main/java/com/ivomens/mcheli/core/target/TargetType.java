package com.ivomens.mcheli.core.target;

/**
 * How a spotted target shows up for the player who spotted it, which picks the colour of its marker. The
 * order is fixed: the client's marker colours are indexed by it.
 */
public enum TargetType {
    /** Not spotted, or not a target this player may spot. */
    NONE,
    /** An animal or another mob that is not a monster. */
    OTHER_MOB,
    MONSTER,
    /** A player in no team. */
    NO_TEAM_PLAYER,
    /** A player in the spotter's own team. */
    SAME_TEAM_PLAYER,
    /** A player in another team. */
    OTHER_TEAM_PLAYER,
    /** A marked point on the ground rather than an entity. */
    POINT
}
