package com.ivomens.mcheli.core.sim.body;

import com.ivomens.mcheli.core.sim.math.Vec3d;

/**
 * What a {@link com.ivomens.mcheli.core.sim.port.Collider} reports after moving a body.
 *
 * @param velocity            the velocity after collision; blocked axes are zeroed
 * @param onGround            whether the body came to rest on something
 * @param horizontalCollision whether a wall stopped it sideways
 */
public record MoveResult(Vec3d velocity, boolean onGround, boolean horizontalCollision) {
}
