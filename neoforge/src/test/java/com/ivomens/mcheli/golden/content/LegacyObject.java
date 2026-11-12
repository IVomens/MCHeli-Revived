package com.ivomens.mcheli.golden.content;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * An object of a class the content rewrite removed (the info adapters), rebuilt from today's records under its
 * old class and field names, so the canonical dumps stay comparable line by line.
 *
 * @param className the removed class, as {@code Class.getName()} gave it
 * @param fields    the old field names with their values today; values may be other legacy objects
 * @param rawLines  writes dump lines that have no field of their own (a HUD's elements), or null
 */
record LegacyObject(String className, Map<String, Object> fields, @Nullable RawLines rawLines) {

    /** Writes {@code path = value} lines below a prefix. */
    @FunctionalInterface
    interface RawLines {

        void write(String prefix, BiConsumer<String, String> lines);
    }

    /** A value written as the given text, such as a reference to other content ({@code <Hud heli>}). */
    record Text(String text) {
    }

    static Builder of(String className) {
        return new Builder(className);
    }

    /** Collects the fields in any order; the dump sorts them. */
    static final class Builder {

        private final String className;
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private RawLines rawLines;

        private Builder(String className) {
            this.className = className;
        }

        Builder put(String field, @Nullable Object value) {
            if (this.fields.containsKey(field)) {
                throw new IllegalStateException("field " + field + " set twice");
            }
            this.fields.put(field, value);
            return this;
        }

        Builder rawLines(RawLines lines) {
            this.rawLines = lines;
            return this;
        }

        LegacyObject build() {
            return new LegacyObject(this.className, this.fields, this.rawLines);
        }
    }
}
