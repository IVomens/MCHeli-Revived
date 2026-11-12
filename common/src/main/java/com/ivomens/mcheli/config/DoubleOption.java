package com.ivomens.mcheli.config;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A decimal number with an inclusive range. A decimal comma is accepted; the file always gets two
 * decimals and a dot. A value that is not a number falls back to the default.
 */
public record DoubleOption(String key, Double defaultValue, double min, double max, RangePolicy outOfRange,
                           List<String> comment, String validSince) implements Option<Double> {

    public DoubleOption {
        comment = List.copyOf(comment);
    }

    public static DoubleOption of(String key, double defaultValue) {
        return new DoubleOption(key, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                RangePolicy.CLAMP, List.of(), "");
    }

    public DoubleOption range(double newMin, double newMax, RangePolicy policy) {
        return new DoubleOption(this.key, this.defaultValue, newMin, newMax, policy, this.comment, this.validSince);
    }

    public DoubleOption range(double newMin, double newMax) {
        return range(newMin, newMax, RangePolicy.CLAMP);
    }

    public DoubleOption atLeast(double newMin) {
        return range(newMin, Double.POSITIVE_INFINITY);
    }

    public DoubleOption describe(String... lines) {
        return new DoubleOption(this.key, this.defaultValue, this.min, this.max, this.outOfRange, List.of(lines),
                this.validSince);
    }

    public DoubleOption since(String version) {
        return new DoubleOption(this.key, this.defaultValue, this.min, this.max, this.outOfRange, this.comment,
                version);
    }

    @Override
    public Double read(Double current, String key, String raw, Consumer<String> problems) {
        double value;
        try {
            value = Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            value = Double.NaN;
        }
        if (Double.isNaN(value)) {
            problems.accept(key + " = " + raw + " is not a number; using the default, " + format(this.defaultValue));
            return this.defaultValue;
        }
        if (value >= this.min && value <= this.max) {
            return value;
        }
        double fallback = this.outOfRange == RangePolicy.CLAMP ? Math.clamp(value, this.min, this.max)
                : this.defaultValue;
        String bound = value < this.min ? "below the minimum, " + format(this.min)
                : "above the maximum, " + format(this.max);
        problems.accept(key + " = " + raw + " is " + bound + "; " + this.outOfRange.using(format(fallback)));
        return fallback;
    }

    @Override
    public List<String> lines(Double value) {
        return List.of(this.key + " = " + format(value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
