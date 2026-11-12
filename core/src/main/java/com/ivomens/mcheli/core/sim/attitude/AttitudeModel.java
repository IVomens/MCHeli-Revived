package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;

/**
 * One vehicle type's attitude: how the stick and keys turn it and how it levels itself.
 * <p>
 * Attitude runs where the pilot is, at whatever step the caller picks: once per tick at 0.05 s, once
 * per render frame (the legacy mode) or at fixed 1/60 s substeps. Rate terms scale with the step;
 * a few legacy terms do not, and are named where they happen.
 *
 * @param <S> what the type reads about its vehicle for one step: definition values and live state
 * @param <M> what the type remembers from step to step
 */
public interface AttitudeModel<S, M> {

    /** The memory of a vehicle nobody has flown yet. */
    M initialMemory();

    /** Memory at the start of a game tick; for the few terms that count per tick instead of per step. */
    default M startTick(M memory) {
        return memory;
    }

    AttitudeState<M> integrate(AttitudeState<M> state, AttitudeInput mouse, ControlInput keys, S setup,
                               AttitudeContext context, float dtSeconds);

    /**
     * The fastest this vehicle can turn on purpose, in degrees per second; the server rejects reports
     * that turned much faster. Self-levelling is left out, the check allows for it separately.
     */
    float maxTurnRate(S setup);

    /** Whether the server steers the heading itself, so the pilot's reported yaw is ignored. */
    default boolean serverOwnsYaw() {
        return false;
    }
}
