package com.ivomens.mcheli.content.emit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ivomens.mcheli.core.content.def.hud.HudCommand;
import com.ivomens.mcheli.core.content.def.hud.HudCommands;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableSchema;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.def.weapon.WeaponSchema;
import com.ivomens.mcheli.content.emit.JsonSchemaEmitter.Header;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The JSON Schema files of every content type, as published in the repository's {@code schemas/} folder: one
 * per type, named after the type ({@code weapon.schema.json}). They are written from the schema declarations by
 * the {@code generateSchemas} build task and never edited by hand; docs/ADDON_AUTHORING.md explains how pack
 * authors use them.
 */
public final class JsonSchemas {

    /** Where the files are published; a file's {@code $id} is this followed by its name. */
    public static final String BASE_URL = "https://raw.githubusercontent.com/IVomens/MCHeli-Revived/main/schemas/";
    /** The extension of the files. */
    public static final String EXTENSION = ".schema.json";

    private static final String IF = "If";

    private JsonSchemas() {
    }

    /**
     * Every file by name, in content type order.
     *
     * @param docs the description of a declaration, or null; see {@link JsonSchemaEmitter#schema}
     */
    public static Map<String, String> files(Function<Object, String> docs) {
        Map<String, String> files = new LinkedHashMap<>();
        for (ContentType type : ContentType.values()) {
            files.put(fileName(type), JsonSchemaEmitter.write(document(type, docs)));
        }
        return files;
    }

    /** The file name of a content type's schema, such as {@code weapon.schema.json}. */
    public static String fileName(ContentType type) {
        return type.name().toLowerCase(Locale.ROOT) + EXTENSION;
    }

    /** The JSON Schema of the files in one content type's folder. */
    public static JsonObject document(ContentType type, Function<Object, String> docs) {
        String folder = "assets/mcheli/" + type.dirName + "/";
        Header header = new Header(BASE_URL + fileName(type), "MCHeli " + noun(type),
                "A YAML file in " + folder + " of an MCHeli addon pack. Generated from the game's schema "
                        + "declarations; do not edit.", Schema.VERSION);
        return switch (type) {
            case WEAPON -> JsonSchemaEmitter.schema(header, WeaponSchema.SCHEMA, docs);
            case THROWABLE -> JsonSchemaEmitter.schema(header, ThrowableSchema.SCHEMA, docs);
            case HELICOPTER -> vehicle(header, VehicleKind.HELICOPTER, docs);
            case PLANE -> vehicle(header, VehicleKind.PLANE, docs);
            case SHIP -> vehicle(header, VehicleKind.SHIP, docs);
            case TANK -> vehicle(header, VehicleKind.TANK, docs);
            case VEHICLE -> vehicle(header, VehicleKind.VEHICLE, docs);
            case HUD -> hud(header, docs);
        };
    }

    private static String noun(ContentType type) {
        return type == ContentType.HUD ? "HUD" : type.name().toLowerCase(Locale.ROOT);
    }

    private static JsonObject vehicle(Header header, VehicleKind kind, Function<Object, String> docs) {
        return JsonSchemaEmitter.schema(header, VehicleSchema.forKind(kind), docs);
    }

    /**
     * A HUD file is a list of commands rather than a map of settings: each item is one or more commands (or a
     * setting), an {@code If} block, or the {@code SchemaVersion} item that starts a format 2 file.
     */
    private static JsonObject hud(Header header, Function<Object, String> docs) {
        JsonSchemaEmitter emitter = JsonSchemaEmitter.forEntries(docs);
        JsonObject commands = new JsonObject();
        commands.addProperty("type", "object");
        commands.addProperty("minProperties", 1);
        JsonObject properties = new JsonObject();
        for (HudCommand<?> command : HudCommands.ALL) {
            JsonObject arguments = command.takesNoArguments() ? new JsonObject()
                    : emitter.entryRef(command.arguments());
            properties.add(command.key(), arguments);
            command.aliases().forEach(alias -> properties.add(alias, arguments));
        }
        JsonObject flag = emitter.shape(ValueShape.flag().note("Draws the HUD at its design size even with HUD "
                + "auto scaling on."));
        HudCommands.IGNORE_AUTO_SCALE.forEach(key -> properties.add(key, flag));
        commands.add("properties", properties);
        commands.addProperty("additionalProperties", false);

        JsonObject otherItems = new JsonObject();
        JsonArray kinds = new JsonArray();
        kinds.add(ref("command"));
        kinds.add(version());
        otherItems.add("anyOf", kinds);
        JsonObject items = item(otherItems);

        JsonObject root = new JsonObject();
        root.addProperty("type", "array");
        root.add("items", items);
        JsonObject document = emitter.finish(header, root);
        JsonObject defs = document.has("$defs") ? document.getAsJsonObject("$defs") : new JsonObject();
        defs.add("command", commands);
        defs.add("if", ifBlock(emitter));
        document.add("$defs", defs);
        return document;
    }

    private static JsonObject ifBlock(JsonSchemaEmitter emitter) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "array");
        block.add("items", item(ref("command")));

        JsonObject properties = new JsonObject();
        properties.add(IF, emitter.shape(ValueShape.either(ValueShape.text(), ValueShape.number())
                .note("A formula; the commands of the block are drawn while it is not 0.")));
        for (String key : List.of("Do", "Then")) {
            properties.add(key, block);
        }
        JsonObject otherwise = block.deepCopy();
        otherwise.addProperty("description", "Drawn while the condition is 0. Format 2 (SchemaVersion: 2) only.");
        properties.add("Else", otherwise);
        JsonObject object = new JsonObject();
        object.addProperty("type", "object");
        object.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add(IF);
        object.add("required", required);
        object.addProperty("additionalProperties", false);
        return object;
    }

    private static JsonObject version() {
        JsonObject versionValue = new JsonObject();
        versionValue.addProperty("type", "integer");
        JsonArray versions = new JsonArray();
        versions.add(1);
        versions.add(2);
        versionValue.add("enum", versions);
        versionValue.addProperty("description", "The HUD format: 2 lets If blocks nest and take Else. Only as the "
                + "first item.");
        JsonObject properties = new JsonObject();
        properties.add("SchemaVersion", versionValue);
        JsonObject item = new JsonObject();
        item.addProperty("type", "object");
        item.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("SchemaVersion");
        item.add("required", required);
        item.addProperty("additionalProperties", false);
        return item;
    }

    /** An item that is an If block when it has an If key, and {@code otherwise} when not. */
    private static JsonObject item(JsonObject otherwise) {
        JsonObject hasIf = new JsonObject();
        hasIf.addProperty("type", "object");
        JsonArray required = new JsonArray();
        required.add(IF);
        hasIf.add("required", required);
        JsonObject item = new JsonObject();
        item.add("if", hasIf);
        item.add("then", ref("if"));
        item.add("else", otherwise);
        return item;
    }

    private static JsonObject ref(String name) {
        JsonObject ref = new JsonObject();
        ref.addProperty("$ref", "#/$defs/" + name);
        return ref;
    }
}
