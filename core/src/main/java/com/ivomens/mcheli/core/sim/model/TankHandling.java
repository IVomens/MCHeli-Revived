package com.ivomens.mcheli.core.sim.model;

/**
 * Tank and car definition values the motion models read.
 *
 * @param gravity         per-tick gravity in air and water
 * @param maxSpeed        top speed over the ground
 * @param motionFactor    rolling friction of tracks, and air drag of both kinds once airborne
 * @param canMoveOnGround whether the engine drives while resting on solid ground
 * @param reverseEnabled  whether the definition has a reverse gear
 * @param floats          whether the hull floats on water
 */
public record TankHandling(VerticalForces.Gravity gravity, float maxSpeed, float motionFactor,
                           boolean canMoveOnGround, boolean reverseEnabled, boolean floats) implements HandlingProfile {
}
