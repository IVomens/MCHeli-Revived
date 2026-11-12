package com.ivomens.mcheli.vehicle.component;

/**
 * The phases of a vehicle tick, in the order they run. Every step of the tick pipeline belongs to one
 * phase, and the phases never interleave.
 */
public enum TickPhase {

    /** The pilot's attitude report and keys, last tick's throttle, and a stopped vehicle's sync. */
    INPUT,
    /** Riding a minecart, riders who just left, and the wreck's crew fire and countdown. */
    LIFECYCLE,
    /** Vanilla's base tick and the vehicle's systems: links, supplies, fuel, damage, countermeasures. */
    SYSTEMS,
    /** The vehicle type's controls, weapons and physics step. */
    PHYSICS,
    /** Everything that follows the hull to where the physics put it: hit boxes, parts, seats, crew. */
    FOLLOW_HULL,
    /** What the next tick compares against: riders, the position history, the end of the attitude step. */
    BOOKKEEPING
}
