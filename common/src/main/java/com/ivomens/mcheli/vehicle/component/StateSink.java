package com.ivomens.mcheli.vehicle.component;

import java.util.Locale;

/**
 * Where a component writes what it holds when the vehicle inspector asks ({@link VehicleComponent#describe}):
 * one named value per line, in the order given. Writing to it never changes the component.
 */
@FunctionalInterface
public interface StateSink {

    void put(String key, String value);

    default void put(String key, boolean value) {
        put(key, value ? "yes" : "no");
    }

    default void put(String key, int value) {
        put(key, Integer.toString(value));
    }

    default void put(String key, double value) {
        put(key, number(value));
    }

    /** A number as the inspector shows it: whole numbers as they are, others with three decimals. */
    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E9) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
