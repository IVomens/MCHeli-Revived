package com.ivomens.mcheli.content.emit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ivomens.mcheli.core.content.schema.EntryList;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Writes a content {@link Schema} as a JSON Schema (draft 2020-12) document, so editors can complete and check
 * YAML files and a pack's CI can validate them without the game. It walks the same declaration the readers use:
 * every key with its spellings, the value shapes the codecs state ({@link ValueShape}), defaults, ranges, the
 * keys a section cannot do without, and the keys the game no longer reads (marked {@code deprecated}).
 * <p>
 * The document is never stricter than the game: what the game reads without a message is valid. Unknown keys,
 * values out of range and values of the wrong type are not, because the game reports them.
 */
public final class JsonSchemaEmitter {

    /** The JSON Schema draft the documents follow. */
    public static final String DRAFT = "https://json-schema.org/draft/2020-12/schema";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String NOT_SUPPORTED = "No longer supported: the game ignores this key and says so once "
            + "per load. You can delete it.";

    private final Function<Object, String> docs;
    private final JsonObject defs = new JsonObject();
    private final Map<Object, String> defNames = new IdentityHashMap<>();
    private final Map<Field<?, ?>, String> paths = new IdentityHashMap<>();

    private JsonSchemaEmitter(Function<Object, String> docs) {
        this.docs = docs;
    }

    /**
     * What a JSON Schema document says about the file as a whole.
     *
     * @param id          the document's {@code $id}, the URL it is published at
     * @param title       the content type, such as {@code MCHeli weapon}
     * @param description a sentence about the files it describes
     * @param version     the content schema version the game reads ({@link Schema#VERSION})
     */
    public record Header(String id, String title, String description, int version) {
    }

    /**
     * The JSON Schema of a key/value content type.
     *
     * @param docs the description of a declaration ({@link Field}, {@link Section}, {@link EntrySchema} or
     *             {@link ValueCodec}), or null when it has none
     */
    public static JsonObject schema(Header header, Schema<?> schema, Function<Object, String> docs) {
        JsonSchemaEmitter emitter = new JsonSchemaEmitter(docs);
        emitter.collectPaths(schema.root(), "");
        JsonObject root = emitter.section(schema.root());
        JsonObject properties = root.getAsJsonObject("properties");
        JsonObject version = new JsonObject();
        version.addProperty("type", "integer");
        version.addProperty("minimum", 1);
        version.addProperty("maximum", header.version());
        version.addProperty("description", "The content schema version the file is written for. Optional; a file "
                + "without it is version 1. This game reads version " + header.version() + ".");
        properties.add(Schema.VERSION_KEY, version);
        return emitter.document(header, root);
    }

    /** Wraps a finished root schema with the document keywords and the definitions it refers to. */
    JsonObject document(Header header, JsonObject root) {
        JsonObject document = new JsonObject();
        document.addProperty("$schema", DRAFT);
        document.addProperty("$id", header.id());
        document.addProperty("title", header.title());
        document.addProperty("description", header.description());
        document.addProperty("x-mcheli-schema-version", header.version());
        root.entrySet().forEach(entry -> document.add(entry.getKey(), entry.getValue()));
        if (!this.defs.isEmpty()) {
            document.add("$defs", this.defs);
        }
        return document;
    }

    /** A document as text: two-space indents, a final newline. */
    public static String write(JsonObject document) {
        return GSON.toJson(document) + "\n";
    }

    /** An emitter for documents that are not one schema, such as the HUD program; see {@link #entryRef}. */
    public static JsonSchemaEmitter forEntries(Function<Object, String> docs) {
        return new JsonSchemaEmitter(docs);
    }

    /** Finishes a document built with {@link #forEntries}. */
    public JsonObject finish(Header header, JsonObject root) {
        return document(header, root);
    }

    /** A reference to the definition of an entry schema, adding the definition the first time. */
    public JsonObject entryRef(EntrySchema<?> entries) {
        String name = this.defNames.get(entries);
        if (name == null) {
            name = uniqueName(entries.what());
            this.defNames.put(entries, name);
            this.defs.add(name, new JsonObject());
            this.defs.add(name, entry(entries));
        }
        JsonObject ref = new JsonObject();
        ref.addProperty("$ref", "#/$defs/" + name);
        return ref;
    }

    /** The JSON Schema of a value shape. */
    public JsonObject shape(ValueShape shape) {
        return ShapeWriter.write(shape);
    }

    private String uniqueName(String what) {
        String base = camel(what);
        String name = base;
        for (int i = 2; this.defs.has(name); i++) {
            name = base + i;
        }
        return name;
    }

    private static String camel(String what) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char c : what.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        return name.isEmpty() ? "entry" : name.toString();
    }

    private <S> void collectPaths(Section<?, S> section, String path) {
        for (Member<S> member : section.members()) {
            String memberPath = path.isEmpty() ? member.key() : path + "." + member.key();
            switch (member) {
                case Field<S, ?> field -> this.paths.putIfAbsent(field, memberPath);
                case Section<S, ?> child -> collectPaths(child, memberPath);
                case KeyAlias<S> alias -> {
                    // its target is collected where it is declared
                }
            }
        }
    }

    private JsonObject entry(EntrySchema<?> entries) {
        JsonObject object = section(entries.root());
        describe(object, this.docs.apply(entries));
        return entries.root().shorthand().map(field -> anyOf(object, field(field))).orElse(object);
    }

    private <S> JsonObject section(Section<?, S> section) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "object");
        describe(object, this.docs.apply(section));
        JsonObject properties = new JsonObject();
        for (Member<S> member : section.members()) {
            JsonObject property = member(member);
            properties.add(member.key(), property);
            for (String alias : member.aliases()) {
                properties.add(alias, aliasOf(property, member.key()));
            }
        }
        for (String key : unsupportedKeys(section)) {
            JsonObject property = new JsonObject();
            property.addProperty("deprecated", true);
            property.addProperty("description", NOT_SUPPORTED);
            properties.add(key, property);
        }
        object.add("properties", properties);
        addRequired(object, section.required());
        object.addProperty("additionalProperties", false);
        return object;
    }

    /** The keys a section cannot do without, each under any of its spellings. */
    private static void addRequired(JsonObject object, List<? extends Field<?, ?>> fields) {
        JsonArray required = new JsonArray();
        JsonArray spelledEither = new JsonArray();
        for (Field<?, ?> field : fields) {
            if (field.aliases().isEmpty()) {
                required.add(field.key());
                continue;
            }
            JsonArray options = new JsonArray();
            for (String key : concat(field.key(), field.aliases())) {
                JsonObject option = new JsonObject();
                JsonArray one = new JsonArray();
                one.add(key);
                option.add("required", one);
                options.add(option);
            }
            JsonObject anyOf = new JsonObject();
            anyOf.add("anyOf", options);
            spelledEither.add(anyOf);
        }
        if (!required.isEmpty()) {
            object.add("required", required);
        }
        if (!spelledEither.isEmpty()) {
            object.add("allOf", spelledEither);
        }
    }

    private static List<String> concat(String first, List<String> rest) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    private <S> JsonObject member(Member<S> member) {
        return switch (member) {
            case Field<S, ?> field -> field(field);
            case Section<S, ?> child -> childSection(child);
            case KeyAlias<S> alias -> keyAlias(alias);
        };
    }

    private JsonObject childSection(Section<?, ?> child) {
        JsonObject object = section(child);
        if (child.isShorthandUnsupported()) {
            JsonObject plain = new JsonObject();
            JsonObject notMap = new JsonObject();
            notMap.addProperty("type", "object");
            plain.add("not", notMap);
            plain.addProperty("deprecated", true);
            plain.addProperty("description", "A plain value here is an old spelling. " + NOT_SUPPORTED);
            return anyOf(object, plain);
        }
        return child.shorthand().map(field -> anyOf(object, field(field))).orElse(object);
    }

    private JsonObject keyAlias(KeyAlias<?> alias) {
        JsonObject object = value(alias.target(), alias.spelling());
        String target = this.paths.getOrDefault(alias.target(), alias.target().key());
        describe(object, "Another place for '" + target + "'; converted files write it there.");
        return object;
    }

    private JsonObject field(Field<?, ?> field) {
        JsonObject object = value(field, field.codec());
        JsonElement shapeNote = object.remove("description");
        String doc = this.docs.apply(field);
        describe(object, doc != null ? doc : this.docs.apply(field.codec()));
        if (shapeNote != null) {
            describe(object, shapeNote.getAsString());
        }
        if (!field.txtKeys().isEmpty()) {
            describe(object, "Legacy TXT key: " + String.join(", ", field.txtKeys()) + ".");
        }
        return object;
    }

    private <T> JsonObject value(Field<?, T> field, ValueCodec<?> codec) {
        if (codec instanceof EntryList.Many<?> many) {
            JsonObject array = new JsonObject();
            array.addProperty("type", "array");
            array.add("items", entryRef(many.schema()));
            return array;
        }
        if (codec instanceof EntryList.One<?> one) {
            return entryRef(one.schema());
        }
        JsonObject object = shape(codec.shape());
        if (codec == field.codec()) {
            addDefault(object, field);
        }
        return object;
    }

    private static <T> void addDefault(JsonObject object, Field<?, T> field) {
        T value = field.defaultFor("a");
        if (value == null || !Objects.equals(value, field.defaultFor("b"))) {
            return; // none, or it depends on the file name
        }
        try {
            JsonElement written = json(field.codec().toYaml(value));
            if (!written.isJsonNull()) {
                object.add("default", written);
            }
        } catch (RuntimeException e) {
            // a default the emitter cannot spell is not shown
        }
    }

    /** A YAML value as the emitter writes it, as JSON. */
    static JsonElement json(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case Boolean flag -> new JsonPrimitive(flag);
            case Float number -> new JsonPrimitive(ShapeWriter.number(Double.parseDouble(Float.toString(number))));
            case Number number -> new JsonPrimitive(ShapeWriter.number(number.doubleValue()));
            case List<?> list -> {
                JsonArray array = new JsonArray();
                list.forEach(item -> array.add(json(item)));
                yield array;
            }
            case Map<?, ?> map -> {
                JsonObject object = new JsonObject();
                map.forEach((key, item) -> object.add(String.valueOf(key), json(item)));
                yield object;
            }
            default -> new JsonPrimitive(String.valueOf(value));
        };
    }

    private static JsonObject aliasOf(JsonObject property, String key) {
        JsonObject alias = property.deepCopy();
        alias.remove("description");
        describe(alias, "Another spelling of '" + key + "'.");
        return alias;
    }

    private static List<String> unsupportedKeys(Section<?, ?> section) {
        List<String> keys = new ArrayList<>();
        for (String candidate : section.unsupportedKeys()) {
            if (section.member(candidate).isEmpty()) {
                keys.add(candidate);
            }
        }
        return keys;
    }

    static JsonObject anyOf(JsonObject first, JsonObject second) {
        JsonObject object = new JsonObject();
        if (first.has("description")) {
            object.add("description", first.get("description"));
        }
        JsonArray options = new JsonArray();
        options.add(first);
        options.add(second);
        object.add("anyOf", options);
        return object;
    }

    /** Adds a sentence to the object's description. */
    static void describe(JsonObject object, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String sentence = text.strip();
        if (object.has("description")) {
            String current = object.get("description").getAsString();
            if (current.contains(sentence)) {
                return;
            }
            sentence = current + " " + sentence;
        }
        object.addProperty("description", sentence);
    }
}
