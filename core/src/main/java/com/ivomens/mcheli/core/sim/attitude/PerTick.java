package com.ivomens.mcheli.core.sim.attitude;

/** Legacy rates were tuned per game tick; these spread them over steps of any length. */
final class PerTick {

    static final float TICKS_PER_SECOND = 20.0F;

    private PerTick() {
    }

    /** Keeps {@code factorPerTick} of a value per tick, whatever the step: rate-invariant. */
    static float decay(double factorPerTick, float dtSeconds) {
        return (float) Math.pow(factorPerTick, dtSeconds * TICKS_PER_SECOND);
    }
}
