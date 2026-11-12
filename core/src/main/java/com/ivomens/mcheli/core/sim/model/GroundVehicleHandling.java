package com.ivomens.mcheli.core.sim.model;

/**
 * Ground vehicle (emplaced gun, light vehicle) definition values the motion model reads.
 *
 * @param gravity    per-tick gravity in air and water
 * @param speedLimit top speed over the ground
 * @param floats     whether the hull floats on water
 * @param canDrive   the definition's {@code isEnableMove}: the throttle keys push the vehicle
 * @param canSteer   the definition's {@code isEnableRot}: the side keys steer the front wheels
 */
public record GroundVehicleHandling(VerticalForces.Gravity gravity, float speedLimit, boolean floats,
                                    boolean canDrive, boolean canSteer) implements HandlingProfile {
}
