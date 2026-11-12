package com.ivomens.mcheli.core.sim.model;

/**
 * Helicopter definition values the motion model reads.
 *
 * @param gravity       per-tick gravity in air and water
 * @param speedLimit    top speed over the ground, blocks per tick
 * @param onGroundPitch the pitch the airframe rests at on its gear
 * @param floats        whether the hull floats on water
 */
public record RotorcraftHandling(VerticalForces.Gravity gravity, float speedLimit, float onGroundPitch,
                                 boolean floats) implements HandlingProfile {
}
