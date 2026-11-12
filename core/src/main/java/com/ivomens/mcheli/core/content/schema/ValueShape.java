package com.ivomens.mcheli.core.content.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a YAML value may look like, as far as a tool outside the game can check it: the JSON Schema files in
 * {@code schemas/} are written from these (see {@code content.emit.JsonSchemaEmitter}). A codec states its
 * shape with {@link ValueCodec#shape()}; a codec that states none accepts anything.
 * <p>
 * A shape describes what the game reads without a warning. It is never stricter than the game: a value the
 * game reads silently passes, and a value the game clamps, reports or rejects does not.
 */
public sealed interface ValueShape {

    /** Anything; the game checks the value itself. */
    record Anything() implements ValueShape {
    }

    /** {@code true} or {@code false}. */
    record Flag() implements ValueShape {
    }

    /**
     * A number; {@code whole} for settings the game keeps as whole numbers. Null bounds are open.
     */
    record Number(boolean whole, Double min, Double max) implements ValueShape {
    }

    /** Text; {@code pattern} (an ECMA 262 regular expression) or null for any text. */
    record Text(String pattern) implements ValueShape {
    }

    /**
     * One of a fixed set of words.
     *
     * @param ignoreCase whether the game reads them in any case
     * @param open       whether the game also reads other text (falling back to a default), so the words are
     *                   suggestions rather than a rule
     */
    record Words(List<String> words, boolean ignoreCase, boolean open) implements ValueShape {

        public Words {
            words = List.copyOf(words);
        }
    }

    /** A list; null bounds are open. */
    record ListOf(ValueShape items, Integer minItems, Integer maxItems) implements ValueShape {
    }

    /** A map of any keys to values of one shape. */
    record MapOf(ValueShape values) implements ValueShape {
    }

    /**
     * A map with known keys.
     *
     * @param keys     the keys in the order they are documented, with their shapes
     * @param required keys the map cannot do without
     */
    record Keys(Map<String, ValueShape> keys, List<String> required) implements ValueShape {

        public Keys {
            keys = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(keys));
            required = List.copyOf(required);
        }
    }

    /** Any of several shapes. */
    record Either(List<ValueShape> options) implements ValueShape {

        public Either {
            options = List.copyOf(options);
        }
    }

    /** A shape with a sentence for the author, such as how the value is spelled. */
    record Noted(ValueShape shape, String note) implements ValueShape {
    }

    static ValueShape anything() {
        return new Anything();
    }

    static ValueShape flag() {
        return new Flag();
    }

    static ValueShape whole() {
        return new Number(true, null, null);
    }

    static ValueShape whole(double min, double max) {
        return new Number(true, min, max);
    }

    static ValueShape number() {
        return new Number(false, null, null);
    }

    static ValueShape number(double min, double max) {
        return new Number(false, min, max);
    }

    static ValueShape text() {
        return new Text(null);
    }

    static ValueShape text(String pattern) {
        return new Text(pattern);
    }

    /** Words the game reads in any case and nothing else. */
    static ValueShape words(List<String> words) {
        return new Words(words, true, false);
    }

    /** Words the game reads in any case; other text falls back silently, so they are only suggested. */
    static ValueShape suggested(List<String> words) {
        return new Words(words, true, true);
    }

    static ValueShape listOf(ValueShape items) {
        return new ListOf(items, null, null);
    }

    /** A list of exactly {@code count} values, such as {@code [x, y, z]}. */
    static ValueShape tuple(int count, ValueShape items) {
        return new ListOf(items, count, count);
    }

    static ValueShape mapOf(ValueShape values) {
        return new MapOf(values);
    }

    static ValueShape either(ValueShape... options) {
        return new Either(List.of(options));
    }

    /** A value, or a list of such values. */
    static ValueShape oneOrMore(ValueShape item) {
        return either(item, listOf(item));
    }

    /** A map with known keys; build it with {@link KeysBuilder#key} and {@link KeysBuilder#required}. */
    static KeysBuilder keys() {
        return new KeysBuilder();
    }

    /** The same shape with a sentence for the author. */
    default ValueShape note(String note) {
        return new Noted(this, note);
    }

    /** Builds a {@link Keys} shape. */
    final class KeysBuilder {

        private final Map<String, ValueShape> keys = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        private KeysBuilder() {
        }

        public KeysBuilder key(String key, ValueShape shape) {
            this.keys.put(key, shape);
            return this;
        }

        public KeysBuilder required(String key, ValueShape shape) {
            this.required.add(key);
            return key(key, shape);
        }

        public ValueShape build() {
            return new Keys(this.keys, this.required);
        }
    }
}
