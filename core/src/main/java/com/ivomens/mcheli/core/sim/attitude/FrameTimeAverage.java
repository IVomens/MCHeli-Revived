package com.ivomens.mcheli.core.sim.attitude;

import java.util.Arrays;

/**
 * The average of the last ten step lengths, which tanks use as their step. It starts filled with
 * zeros, so the first steps after boarding are short and the hull eases into following the ground.
 * Immutable: each step makes a new one.
 */
public final class FrameTimeAverage {

    private static final int LENGTH = 10;

    private final float[] values;
    private final int next;

    private FrameTimeAverage(float[] values, int next) {
        this.values = values;
        this.next = next;
    }

    public static FrameTimeAverage empty() {
        return new FrameTimeAverage(new float[LENGTH], 0);
    }

    /** This average with {@code seconds} replacing the oldest value. */
    public FrameTimeAverage with(float seconds) {
        float[] copy = Arrays.copyOf(this.values, LENGTH);
        copy[this.next] = seconds;
        return new FrameTimeAverage(copy, (this.next + 1) % LENGTH);
    }

    /** Summed in slot order, not age order, as the legacy filter did; the rounding depends on it. */
    public float average() {
        float sum = 0.0F;
        for (float value : this.values) {
            sum += value;
        }
        return sum / LENGTH;
    }
}
