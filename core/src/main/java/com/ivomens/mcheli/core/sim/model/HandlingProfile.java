package com.ivomens.mcheli.core.sim.model;

/**
 * The definition values one vehicle type's motion model reads. Built once per content load, so the
 * models never see the parsed definition itself.
 */
public sealed interface HandlingProfile
        permits RotorcraftHandling, FixedWingHandling, TankHandling, GroundVehicleHandling {

    /** Per-tick gravity in air and water. */
    VerticalForces.Gravity gravity();

    /** Whether the hull floats on water. */
    boolean floats();
}
