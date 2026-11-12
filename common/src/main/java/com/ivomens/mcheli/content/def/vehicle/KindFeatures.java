package com.ivomens.mcheli.content.def.vehicle;

/**
 * The settings of one vehicle kind: YAML {@code HeliFeatures}, {@code PlaneFeatures}, {@code ShipFeatures},
 * {@code TankFeatures} or {@code VehicleFeatures}. A file has the section of its own kind; the values of
 * the other kinds keep their defaults.
 *
 * @param foldableBlades    helicopters: the rotor blades fold away
 * @param variableSweepWing planes and ships: the wing sweeps back with speed
 * @param sweptSpeed        planes and ships: the top speed with the wing swept back
 * @param weightType        tanks: how the weight sits, by the number of a {@code TankWeight}
 * @param weightedCenterZ   tanks: where the weight sits along the vehicle
 * @param canMove           ground vehicles: the vehicle can drive
 * @param canRotate         ground vehicles: the vehicle can turn
 */
public record KindFeatures(boolean foldableBlades, boolean variableSweepWing, float sweptSpeed, Vtol vtol,
                           int weightType, float weightedCenterZ, boolean canMove, boolean canRotate) {

    /** {@code EnableVtol} of planes and ships: vertical take-off, and the turn rates while hovering. */
    public record Vtol(boolean enabled, boolean byDefault, float yaw, float pitch) {
    }
}
