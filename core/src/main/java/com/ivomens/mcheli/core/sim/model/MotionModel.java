package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MotionStep;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;
import com.ivomens.mcheli.core.sim.port.StepContext;

/**
 * One vehicle type's server-side movement for one tick: forces, impulses, speed limits and
 * friction, then the move through the collider, then damping, in the legacy order.
 *
 * @param <H> the handling values the type reads from its definition
 */
public interface MotionModel<H extends HandlingProfile> {

    MotionStep step(BodyState body, PropulsionState propulsion, ControlInput input, H handling, StepContext context);
}
