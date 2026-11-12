package com.ivomens.mcheli.config;

import java.util.List;
import java.util.function.Consumer;

/**
 * One option of a {@code key = value} config file: its key, default, the comment written above it and
 * how a line of text becomes a value. Options never throw on bad input; they report the problem and
 * fall back to a usable value.
 *
 * @param <T> the value type
 */
public sealed interface Option<T> permits BoolOption, IntOption, DoubleOption, StringOption, ListOption {

    /** The key as it is written. Reading ignores case. */
    String key();

    T defaultValue();

    /** Lines written above the option, without the leading {@code ;}. */
    List<String> comment();

    /**
     * Oldest file version ({@code MOD_Version}, "x.y.z") whose value is still used; values from older
     * files are ignored. Empty when any version will do.
     */
    String validSince();

    default boolean matches(String key) {
        return key().equalsIgnoreCase(key);
    }

    /** The value an option starts from while a file is read; lists start empty, not at their default. */
    default T unreadValue() {
        return defaultValue();
    }

    /**
     * The value after one more line of the file.
     *
     * @param current  the value so far
     * @param key      the key as the line spelled it
     * @param raw      the text after {@code =}, trimmed
     * @param problems receives a sentence for each value that could not be used as written
     */
    T read(T current, String key, String raw, Consumer<String> problems);

    /** The {@code key = value} lines for a value. */
    List<String> lines(T value);
}
