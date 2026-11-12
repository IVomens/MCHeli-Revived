package com.ivomens.mcheli.config;

import java.util.HashMap;
import java.util.Map;

/**
 * The values of a config file, as an immutable map from option to value. An option that was never
 * set reads as its default.
 */
public final class ConfigValues {

    public static final ConfigValues DEFAULTS = new ConfigValues(Map.of());

    private final Map<Option<?>, Object> values;

    private ConfigValues(Map<Option<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Option<T> option) {
        Object value = this.values.get(option);
        return value == null ? option.defaultValue() : (T) value;
    }

    /** Whether the option was set, by a file line or by {@link #with}. */
    public boolean isSet(Option<?> option) {
        return this.values.containsKey(option);
    }

    public <T> ConfigValues with(Option<T> option, T value) {
        Map<Option<?>, Object> changed = new HashMap<>(this.values);
        changed.put(option, value);
        return new ConfigValues(changed);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfigValues that && this.values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }
}
