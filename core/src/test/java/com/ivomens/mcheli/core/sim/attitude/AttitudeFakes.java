package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.AttitudeInput;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.port.SimSettings;

/** Skies, ground and settings for the attitude model tests. */
final class AttitudeFakes {

    static final AttitudeTerrain HIGH_IN_THE_AIR = new AttitudeTerrain(true, true, true, 0.0);
    static final AttitudeTerrain ON_THE_GROUND = new AttitudeTerrain(false, false, false, 0.0);
    static final SimSettings EULER = new SimSettings(false, false, false, SimSettings.TiltLimit.NONE);
    static final SimSettings QUATERNION = new SimSettings(false, true, false, SimSettings.TiltLimit.NONE);
    static final ControlInput NO_KEYS = new ControlInput(false, false, false, false, false);
    static final ControlInput LEFT = new ControlInput(false, false, true, false, false);
    static final ControlInput RIGHT = new ControlInput(false, false, false, true, false);
    static final ControlInput BACK_LEFT = new ControlInput(false, true, true, false, false);
    static final Attitude LEVEL = new Attitude(0.0F, 0.0F, 0.0F);
    static final StickHandling STICK = StickHandling.of(1.0F, 1.0F, 1.0F, RotationLimits.NONE);

    private AttitudeFakes() {
    }

    static AttitudeContext flying(SimSettings settings) {
        return new AttitudeContext(HIGH_IN_THE_AIR, settings, false, true, false);
    }

    static AttitudeContext grounded() {
        return new AttitudeContext(ON_THE_GROUND, EULER, false, true, false);
    }

    static AttitudeInput stick(float x, float y) {
        return new AttitudeInput(x, y, false);
    }

    /** Runs {@code steps} steps of {@code dt} with the same input. */
    static <S, M> AttitudeState<M> run(AttitudeModel<S, M> model, AttitudeState<M> start, AttitudeInput mouse,
                                       ControlInput keys, S setup, AttitudeContext context, float dt, int steps) {
        AttitudeState<M> state = start;
        for (int i = 0; i < steps; i++) {
            state = model.integrate(state, mouse, keys, setup, context, dt);
        }
        return state;
    }

    /**
     * Runs whole ticks of {@code substeps} steps each, starting a new tick in between as the
     * entity does; for terms that count per tick.
     */
    static <S, M> AttitudeState<M> runTicks(AttitudeModel<S, M> model, AttitudeState<M> start, AttitudeInput mouse,
                                            ControlInput keys, S setup, AttitudeContext context, int ticks,
                                            int substeps) {
        AttitudeState<M> state = start;
        float dt = 0.05F / substeps;
        for (int tick = 0; tick < ticks; tick++) {
            state = state.withMemory(model.startTick(state.memory()));
            state = run(model, state, mouse, keys, setup, context, dt, substeps);
        }
        return state;
    }
}
