package com.ivomens.mcheli.core.sim.port;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MoveResult;

/** Moves a body through the world and resolves collisions. */
public interface Collider {

    /**
     * Puts the body at its attitude and velocity, then moves it by one tick of that velocity.
     * In game this is vanilla {@code Entity.move}, step-up and wall damage included.
     */
    MoveResult move(BodyState body);
}
