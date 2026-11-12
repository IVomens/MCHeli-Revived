package com.ivomens.mcheli.api.vehicle.extension;

/**
 * Where in a vehicle's tick a {@link VehicleBehaviour} ticks. MCHeli builds the vehicle tick from fixed phases
 * (input, lifecycle, systems, physics, following the hull, bookkeeping); a behaviour's tick joins one of them and
 * is checked against the tick order like MCHeli's own components.
 *
 * <p>Since API 1.1. A minor version may add constants, so a {@code switch} over them needs a {@code default}
 * branch.
 */
public enum BehaviourTiming {

    /**
     * After the vehicle's systems (fuel, damage, countermeasures) and before its controls and movement: the vehicle
     * is still where the last tick left it. A velocity set here is what the movement starts from.
     */
    BEFORE_MOVEMENT,

    /**
     * After the movement, once the hit boxes, parts and seats have followed the hull: the vehicle is where it ends
     * the tick. Effects and anything attached to the vehicle belong here.
     */
    AFTER_MOVEMENT
}
