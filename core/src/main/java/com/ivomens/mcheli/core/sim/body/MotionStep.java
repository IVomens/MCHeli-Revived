package com.ivomens.mcheli.core.sim.body;

import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;

/**
 * The outcome of one motion step: the body after its move and the propulsion it ended with.
 *
 * @param body       final velocity (after post-move damping), attitude and ground contact
 * @param propulsion propulsion state, with the cruise speed brought up to date
 */
public record MotionStep(BodyState body, PropulsionState propulsion) {
}
