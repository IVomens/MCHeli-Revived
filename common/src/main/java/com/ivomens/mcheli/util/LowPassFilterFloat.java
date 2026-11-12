package com.ivomens.mcheli.util;

import java.util.Arrays;

/** Moving average over the last {@code filterLength} values; starts filled with zeros. */
public class LowPassFilterFloat {

    // A primitive ring buffer: put() runs every frame and must not box.
    private final float[] values;
    private int next;

    public LowPassFilterFloat(int filterLength) {
        this.values = new float[Math.max(1, filterLength)];
    }

    public void clear() {
        Arrays.fill(this.values, 0.0F);
    }

    public void put(float value) {
        this.values[this.next] = value;
        this.next = (this.next + 1) % this.values.length;
    }

    public float getAvg() {
        float sum = 0.0F;
        for (float value : this.values) {
            sum += value;
        }
        return sum / this.values.length;
    }
}
