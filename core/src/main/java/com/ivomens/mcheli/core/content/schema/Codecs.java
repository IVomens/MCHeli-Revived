package com.ivomens.mcheli.core.content.schema;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The plain value spellings shared by every content type, and the type checks the structured codecs use.
 * <p>
 * The legacy readers are kept exactly: a TXT flag is on only for the word {@code true} (any case), YAML
 * flags must be real booleans, YAML numbers may be written with or without a fraction, and a value
 * outside a setting's range is clamped. A clamped value is now reported instead of passing silently.
 */
public final class Codecs {

    public static final ValueCodec<Boolean> BOOLEAN = ValueCodec.of(
            raw -> require(raw, Boolean.class, "true/false"),
            text -> text.equalsIgnoreCase("true"),
            value -> value).shaped(ValueShape.flag());

    public static final ValueCodec<Integer> INT = ValueCodec.of(
            raw -> number(raw).intValue(),
            Codecs::parseInt,
            value -> value).shaped(ValueShape.whole());

    public static final ValueCodec<Float> FLOAT = ValueCodec.of(
            raw -> number(raw).floatValue(),
            Codecs::parseFloat,
            value -> value).shaped(ValueShape.number());

    public static final ValueCodec<Double> DOUBLE = ValueCodec.of(
            raw -> number(raw).doubleValue(),
            Codecs::parseDouble,
            value -> value).shaped(ValueShape.number());

    /** Text with the surrounding blanks removed. */
    public static final ValueCodec<String> TEXT = ValueCodec.of(
            raw -> text(raw).trim(),
            String::trim,
            value -> value).shaped(ValueShape.text());

    /** Text compared without regard to case, such as a weapon type or a model name. */
    public static final ValueCodec<String> LOWER_TEXT = ValueCodec.of(
            raw -> text(raw).trim().toLowerCase(Locale.ROOT),
            text -> text.toLowerCase(Locale.ROOT).trim(),
            value -> value).shaped(ValueShape.text());

    /** Who made the file: text, or a list of names joined with commas. */
    public static final ValueCodec<String> AUTHOR = ValueCodec.of(
            Codecs::author,
            String::trim,
            value -> value).shaped(ValueShape.either(ValueShape.text(), ValueShape.listOf(ValueShape.anything()))
            .note("A name, or a list of names."));

    /** A whole number that YAML must spell as one, such as an old numeric item id. */
    public static final ValueCodec<Integer> STRICT_INT = ValueCodec.of(
            raw -> require(raw, Integer.class, "a whole number"),
            Codecs::parseInt,
            value -> value).shaped(ValueShape.whole());

    private Codecs() {
    }

    private static String author(Object raw) {
        if (raw instanceof List<?> authors) {
            return authors.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(author -> !author.isEmpty())
                    .collect(Collectors.joining(", "));
        }
        return String.valueOf(raw).trim();
    }

    /** A whole number clamped to {@code min..max}. */
    public static ValueCodec<Integer> ints(int min, int max) {
        return new ValueCodec<>() {
            @Override
            public Integer fromYaml(Object raw, Integer previous, ReadContext context) {
                return clamp(number(raw).intValue(), min, max, context);
            }

            @Override
            public Integer fromText(String text, Integer previous, ReadContext context) {
                return clamp(parseInt(text), min, max, context);
            }

            @Override
            public Object toYaml(Integer value) {
                return value;
            }

            @Override
            public ValueShape shape() {
                return ValueShape.whole(min, max);
            }
        };
    }

    /** A number clamped to {@code min..max}. */
    public static ValueCodec<Float> floats(float min, float max) {
        return new ValueCodec<>() {
            @Override
            public Float fromYaml(Object raw, Float previous, ReadContext context) {
                return clamp(number(raw).floatValue(), min, max, context);
            }

            @Override
            public Float fromText(String text, Float previous, ReadContext context) {
                return clamp(parseFloat(text), min, max, context);
            }

            @Override
            public Object toYaml(Float value) {
                return value;
            }

            @Override
            public ValueShape shape() {
                return ValueShape.number(min, max);
            }
        };
    }

    public static int clamp(int value, int min, int max, ReadContext context) {
        int clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            context.warn(value + " is outside " + min + " to " + max + ", " + clamped + " is used");
        }
        return clamped;
    }

    public static float clamp(float value, float min, float max, ReadContext context) {
        float clamped = Math.max(min, Math.min(max, value));
        if (Float.compare(clamped, value) != 0 && !Float.isNaN(value)) {
            context.warn(value + " is outside " + min + " to " + max + ", " + clamped + " is used");
        }
        return clamped;
    }

    public static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ContentValueException("expected a whole number but got \"" + text + "\"");
        }
    }

    public static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            throw new ContentValueException("expected a number but got \"" + text + "\"");
        }
    }

    public static double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new ContentValueException("expected a number but got \"" + text + "\"");
        }
    }

    /** The comma separated values of a TXT line, as the legacy parser split them. */
    public static String[] splitTxt(String text) {
        return text.split("\\s*,\\s*");
    }

    public static Number number(Object raw) {
        return require(raw, Number.class, "a number");
    }

    public static String text(Object raw) {
        return require(raw, String.class, "a text value");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object raw) {
        return require(raw, Map.class, "a section of keys");
    }

    public static List<?> list(Object raw) {
        return require(raw, List.class, "a list");
    }

    /** {@code raw} as a {@code type}, or a message naming what was expected and what the file has. */
    public static <T> T require(Object raw, Class<T> type, String expected) {
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        throw new ContentValueException("expected " + expected + " but got " + describe(raw));
    }

    /** How a YAML value reads to a pack author: {@code the whole number 3}, {@code the text "fast"}. */
    public static String describe(Object raw) {
        return switch (raw) {
            case null -> "nothing";
            case String text -> "the text \"" + text + "\"";
            case Integer whole -> "the whole number " + whole;
            case Long whole -> "the whole number " + whole;
            case Number number -> "the number " + number;
            case Boolean flag -> flag.toString();
            case Map<?, ?> map -> "a section of keys";
            case List<?> list -> "a list";
            default -> raw.getClass().getSimpleName();
        };
    }
}
