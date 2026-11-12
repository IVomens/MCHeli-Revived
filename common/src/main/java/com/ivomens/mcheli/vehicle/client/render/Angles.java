package com.ivomens.mcheli.vehicle.client.render;

import java.util.Arrays;

/**
 * An immutable list of floats (angles, slides, recoil amounts) for the render snapshot, without
 * boxing every value.
 */
public final class Angles {

    public static final Angles NONE = new Angles(new float[0]);

    private final float[] values;

    private Angles(float[] values) {
        this.values = values;
    }

    /** Takes over {@code values}; the caller must not change the array afterwards. */
    static Angles wrap(float[] values) {
        return values.length == 0 ? NONE : new Angles(values);
    }

    public static Angles of(float... values) {
        return wrap(values.clone());
    }

    public int size() {
        return this.values.length;
    }

    public float get(int index) {
        return this.values[index];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Angles angles && Arrays.equals(this.values, angles.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.values);
    }

    @Override
    public String toString() {
        return Arrays.toString(this.values);
    }
}
