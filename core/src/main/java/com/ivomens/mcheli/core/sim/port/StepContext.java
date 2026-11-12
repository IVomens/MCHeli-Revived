package com.ivomens.mcheli.core.sim.port;

/**
 * The world as one motion step sees it.
 *
 * @param terrain  probes around the body
 * @param collider moves the body
 * @param random   the vehicle's random numbers
 */
public record StepContext(TerrainProbe terrain, Collider collider, SimRandom random) {
}
