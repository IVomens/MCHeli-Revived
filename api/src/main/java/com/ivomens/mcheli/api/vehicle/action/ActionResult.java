package com.ivomens.mcheli.api.vehicle.action;

/**
 * What came of a {@link VehicleControl} action: done, nothing to do, or why it was not done.
 *
 * <p><b>Stability.</b> Since API 1.1. A minor version may add constants, so a {@code switch} over them needs a
 * {@code default} branch.
 */
public enum ActionResult {
    /** The action was carried out and changed the vehicle. */
    SUCCESS,
    /** The vehicle already was as asked (full health, full tank, rider already in that seat, ...). */
    NO_CHANGE,
    /** The vehicle has left its level (removed, unloaded or changed dimension). */
    VEHICLE_REMOVED,
    /** The vehicle is a wreck, which cannot be repaired, refuelled, rearmed or boarded. */
    VEHICLE_DESTROYED,
    /** The vehicle has no seat with that id. */
    INVALID_SEAT,
    /** Nobody sits in that seat. */
    SEAT_EMPTY,
    /** Someone else sits in that seat. */
    SEAT_OCCUPIED,
    /** The seat shares an exclusion group with a seat that is taken, as the vehicle type defines. */
    SEAT_BLOCKED,
    /** The entity is not aboard this vehicle. */
    NOT_ABOARD,
    /** The vehicle has no weapon in that slot. */
    NO_SUCH_WEAPON,
    /** The vehicle type has no fuel tank. */
    NO_FUEL_TANK,
    /** The vehicle has no owner, and only a vehicle with one can be locked. */
    NO_OWNER,
    /** The server switched vehicle locking off ({@code VehicleLocking} in the admin config). */
    LOCKING_DISABLED,
    /** The game refused: the vehicle ignored the damage, or the entity could not ride. */
    REFUSED;

    /** Whether the vehicle is now as asked: {@link #SUCCESS} or {@link #NO_CHANGE}. */
    public boolean isSuccess() {
        return this == SUCCESS || this == NO_CHANGE;
    }
}
