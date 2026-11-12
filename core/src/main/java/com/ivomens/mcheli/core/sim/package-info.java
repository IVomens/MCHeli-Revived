/**
 * The vehicle physics core: plain Java that moves a body from one tick to the next.
 * <p>
 * Nothing here knows about Minecraft. The world reaches the models through the ports in
 * {@link com.ivomens.mcheli.core.sim.port}: terrain probes, collision, randomness and settings. The
 * entities fill those ports and apply what the models return, so the same models run in game and in
 * plain unit tests.
 * <p>
 * The models reproduce the legacy movement bit for bit: the same float and double types, the same
 * operation order and the same sine table. Quirks that matter for that are kept on purpose and
 * named where they happen.
 */
package com.ivomens.mcheli.core.sim;
