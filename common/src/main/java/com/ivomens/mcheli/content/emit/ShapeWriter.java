package com.ivomens.mcheli.content.emit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import java.util.Map;

/** Writes a {@link ValueShape} as JSON Schema keywords; see {@link JsonSchemaEmitter}. */
final class ShapeWriter {

    private ShapeWriter() {
    }

    static JsonObject write(ValueShape shape) {
        return switch (shape) {
            case ValueShape.Anything anything -> new JsonObject();
            case ValueShape.Flag flag -> type("boolean");
            case ValueShape.Number number -> number(number);
            case ValueShape.Text text -> text(text);
            case ValueShape.Words words -> words(words);
            case ValueShape.ListOf list -> list(list);
            case ValueShape.MapOf map -> {
                JsonObject object = type("object");
                object.add("additionalProperties", write(map.values()));
                yield object;
            }
            case ValueShape.Keys keys -> keys(keys);
            case ValueShape.Either either -> {
                JsonObject object = new JsonObject();
                JsonArray options = new JsonArray();
                either.options().forEach(option -> options.add(write(option)));
                object.add("anyOf", options);
                yield object;
            }
            case ValueShape.Noted noted -> {
                JsonObject object = write(noted.shape());
                JsonSchemaEmitter.describe(object, noted.note());
                yield object;
            }
        };
    }

    /** A whole number as an integer, so ranges read {@code 1} rather than {@code 1.0}. */
    static Number number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? (Number) (long) value : (Number) value;
    }

    private static JsonObject type(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }

    private static JsonObject number(ValueShape.Number shape) {
        JsonObject object = type(shape.whole() ? "integer" : "number");
        if (shape.min() != null) {
            object.addProperty("minimum", number(shape.min()));
        }
        if (shape.max() != null) {
            object.addProperty("maximum", number(shape.max()));
        }
        if (shape.min() != null || shape.max() != null) {
            JsonSchemaEmitter.describe(object, "The game clamps a value outside "
                    + format(shape.min(), "any") + " to " + format(shape.max(), "any") + " and says so.");
        }
        return object;
    }

    private static String format(Double value, String open) {
        return value == null ? open : String.valueOf(number(value));
    }

    private static JsonObject text(ValueShape.Text shape) {
        JsonObject object = type("string");
        if (shape.pattern() != null) {
            object.addProperty("pattern", shape.pattern());
        }
        return object;
    }

    private static JsonObject words(ValueShape.Words shape) {
        JsonObject object = type("string");
        JsonArray words = new JsonArray();
        shape.words().forEach(words::add);
        JsonObject exact = new JsonObject();
        exact.add("enum", words);
        JsonArray options = new JsonArray();
        options.add(exact);
        if (shape.open()) {
            options.add(type("string"));
        } else if (shape.ignoreCase()) {
            JsonObject anyCase = new JsonObject();
            anyCase.addProperty("pattern", anyCase(shape));
            options.add(anyCase);
        }
        object.add("anyOf", options);
        if (shape.ignoreCase()) {
            JsonSchemaEmitter.describe(object, "Any case.");
        }
        return object;
    }

    /** The words as a pattern that ignores case, since JSON Schema patterns have no flags. */
    private static String anyCase(ValueShape.Words shape) {
        StringBuilder pattern = new StringBuilder("^\\s*(?:");
        for (int i = 0; i < shape.words().size(); i++) {
            if (i > 0) {
                pattern.append('|');
            }
            for (char c : shape.words().get(i).toCharArray()) {
                char upper = Character.toUpperCase(c);
                char lower = Character.toLowerCase(c);
                if (upper != lower) {
                    pattern.append('[').append(upper).append(lower).append(']');
                } else if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                    pattern.append('\\').append(c);
                } else {
                    pattern.append(c);
                }
            }
        }
        return pattern.append(")\\s*$").toString();
    }

    private static JsonObject list(ValueShape.ListOf shape) {
        JsonObject object = type("array");
        object.add("items", write(shape.items()));
        if (shape.minItems() != null) {
            object.addProperty("minItems", shape.minItems());
        }
        if (shape.maxItems() != null) {
            object.addProperty("maxItems", shape.maxItems());
        }
        return object;
    }

    private static JsonObject keys(ValueShape.Keys shape) {
        JsonObject object = type("object");
        JsonObject properties = new JsonObject();
        for (Map.Entry<String, ValueShape> key : shape.keys().entrySet()) {
            properties.add(key.getKey(), write(key.getValue()));
        }
        object.add("properties", properties);
        if (!shape.required().isEmpty()) {
            JsonArray required = new JsonArray();
            shape.required().forEach(required::add);
            object.add("required", required);
        }
        object.addProperty("additionalProperties", false);
        return object;
    }
}
