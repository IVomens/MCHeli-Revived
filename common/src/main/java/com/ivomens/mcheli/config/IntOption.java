package com.ivomens.mcheli.config;

import java.util.List;
import java.util.function.Consumer;

/** A whole number with an inclusive range. A value that is not a number falls back to the default. */
public record IntOption(String key, Integer defaultValue, int min, int max, RangePolicy outOfRange,
                        List<String> comment, String validSince) implements Option<Integer> {

    public IntOption {
        comment = List.copyOf(comment);
    }

    public static IntOption of(String key, int defaultValue) {
        return new IntOption(key, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE, RangePolicy.CLAMP, List.of(),
                "");
    }

    public IntOption range(int newMin, int newMax, RangePolicy policy) {
        return new IntOption(this.key, this.defaultValue, newMin, newMax, policy, this.comment, this.validSince);
    }

    public IntOption range(int newMin, int newMax) {
        return range(newMin, newMax, RangePolicy.CLAMP);
    }

    public IntOption describe(String... lines) {
        return new IntOption(this.key, this.defaultValue, this.min, this.max, this.outOfRange, List.of(lines),
                this.validSince);
    }

    @Override
    public Integer read(Integer current, String key, String raw, Consumer<String> problems) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            problems.accept(key + " = " + raw + " is not a whole number; using the default, " + this.defaultValue);
            return this.defaultValue;
        }
        if (value >= this.min && value <= this.max) {
            return value;
        }
        int fallback = this.outOfRange == RangePolicy.CLAMP ? Math.clamp(value, this.min, this.max) : this.defaultValue;
        String bound = value < this.min ? "below the minimum, " + this.min : "above the maximum, " + this.max;
        problems.accept(key + " = " + raw + " is " + bound + "; " + this.outOfRange.using(String.valueOf(fallback)));
        return fallback;
    }

    @Override
    public List<String> lines(Integer value) {
        return List.of(this.key + " = " + value);
    }
}
