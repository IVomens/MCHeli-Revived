package com.ivomens.mcheli.content.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Converts one legacy 1.12 recipe object to 1.21 recipe JSON: shaped, shapeless and smelting recipes, their
 * load conditions, ore dictionary and constant ingredients, and 1.12 item names with metadata.
 */
public final class LegacyRecipeConverter {

    /** Constants that refer to constants: deeper than this is taken for a loop. */
    private static final int MAX_CONSTANT_DEPTH = 8;
    private static final int DEFAULT_COOKING_TIME = 200;

    private LegacyRecipeConverter() {
    }

    /**
     * Converts one legacy recipe object.
     *
     * @param constants  the pack's named ingredients, used as {@code #name}
     * @param itemExists item id validator, or null to accept every item
     */
    public static JsonObject convert(JsonObject legacy, Map<String, JsonElement> constants,
                                     @Nullable Predicate<ResourceLocation> itemExists)
            throws RecipeConversionException {
        String type = legacy.has("type") ? legacy.get("type").getAsString() : "";
        JsonObject out = new JsonObject();
        if (legacy.has("conditions")) {
            out.add("neoforge:conditions", conditions(legacy.get("conditions")));
        }
        switch (withoutNamespace(type)) {
            case "ore_shaped", "crafting_shaped", "shaped" -> shaped(legacy, out, constants, itemExists);
            case "ore_shapeless", "crafting_shapeless", "shapeless" -> shapeless(legacy, out, constants, itemExists);
            case "smelting" -> smelting(legacy, out, constants, itemExists);
            default -> throw new RecipeConversionException("unsupported recipe type '" + type + "'");
        }
        if (!legacy.has("result")) {
            throw new RecipeConversionException("missing 'result'");
        }
        out.add("result", result(legacy.get("result"), itemExists));
        return out;
    }

    private static void shaped(JsonObject legacy, JsonObject out, Map<String, JsonElement> constants,
                               @Nullable Predicate<ResourceLocation> itemExists) throws RecipeConversionException {
        out.addProperty("type", "minecraft:crafting_shaped");
        copyGroup(legacy, out);
        if (!legacy.has("pattern") || !legacy.get("pattern").isJsonArray()) {
            throw new RecipeConversionException("missing 'pattern'");
        }
        JsonArray pattern = legacy.getAsJsonArray("pattern");
        out.add("pattern", pattern.deepCopy());
        if (!legacy.has("key") || !legacy.get("key").isJsonObject()) {
            throw new RecipeConversionException("missing 'key'");
        }
        JsonObject key = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : legacy.getAsJsonObject("key").entrySet()) {
            key.add(entry.getKey(), ingredient(entry.getValue(), constants, itemExists, 0));
        }
        for (JsonElement row : pattern) {
            String text = row.getAsString();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c != ' ' && !key.has(String.valueOf(c))) {
                    throw new RecipeConversionException("pattern uses undefined key '" + c + "'");
                }
            }
        }
        out.add("key", key);
    }

    private static void shapeless(JsonObject legacy, JsonObject out, Map<String, JsonElement> constants,
                                  @Nullable Predicate<ResourceLocation> itemExists) throws RecipeConversionException {
        out.addProperty("type", "minecraft:crafting_shapeless");
        copyGroup(legacy, out);
        if (!legacy.has("ingredients") || !legacy.get("ingredients").isJsonArray()) {
            throw new RecipeConversionException("missing 'ingredients'");
        }
        JsonArray ingredients = new JsonArray();
        for (JsonElement element : legacy.getAsJsonArray("ingredients")) {
            ingredients.add(ingredient(element, constants, itemExists, 0));
        }
        if (ingredients.isEmpty()) {
            throw new RecipeConversionException("no ingredients");
        }
        out.add("ingredients", ingredients);
    }

    private static void smelting(JsonObject legacy, JsonObject out, Map<String, JsonElement> constants,
                                 @Nullable Predicate<ResourceLocation> itemExists) throws RecipeConversionException {
        out.addProperty("type", "minecraft:smelting");
        copyGroup(legacy, out);
        out.add("ingredient", ingredient(legacy.get("ingredient"), constants, itemExists, 0));
        out.addProperty("experience", legacy.has("experience") ? legacy.get("experience").getAsFloat() : 0.0F);
        out.addProperty("cookingtime", legacy.has("cookingtime") ? legacy.get("cookingtime").getAsInt()
                : DEFAULT_COOKING_TIME);
    }

    private static void copyGroup(JsonObject legacy, JsonObject out) {
        if (legacy.has("group")) {
            out.add("group", legacy.get("group"));
        }
    }

    private static JsonArray conditions(JsonElement conditions) throws RecipeConversionException {
        JsonArray out = new JsonArray();
        if (!conditions.isJsonArray()) {
            throw new RecipeConversionException("'conditions' must be an array");
        }
        for (JsonElement element : conditions.getAsJsonArray()) {
            out.add(condition(element));
        }
        return out;
    }

    private static JsonObject condition(JsonElement element) throws RecipeConversionException {
        if (!element.isJsonObject()) {
            throw new RecipeConversionException("invalid condition");
        }
        JsonObject legacy = element.getAsJsonObject();
        String type = legacy.has("type") ? legacy.get("type").getAsString() : "";
        String name = withoutNamespace(type);
        JsonObject out = new JsonObject();
        switch (name) {
            case "mod_loaded" -> {
                out.addProperty("type", "neoforge:mod_loaded");
                out.add("modid", legacy.get("modid"));
            }
            case "item_exists" -> {
                out.addProperty("type", "neoforge:item_exists");
                out.add("item", legacy.get("item"));
            }
            case "not" -> {
                out.addProperty("type", "neoforge:not");
                out.add("value", condition(legacy.get("value")));
            }
            case "and", "or" -> {
                out.addProperty("type", "neoforge:" + name);
                JsonArray values = new JsonArray();
                for (JsonElement value : legacy.getAsJsonArray("values")) {
                    values.add(condition(value));
                }
                out.add("values", values);
            }
            case "true" -> out.addProperty("type", "neoforge:true");
            case "false" -> out.addProperty("type", "neoforge:false");
            default -> throw new RecipeConversionException("unsupported condition '" + type + "'");
        }
        return out;
    }

    private static JsonObject result(JsonElement element, @Nullable Predicate<ResourceLocation> itemExists)
            throws RecipeConversionException {
        JsonElement result = element.isJsonPrimitive() ? itemObject(element) : element;
        if (!result.isJsonObject()) {
            throw new RecipeConversionException("invalid 'result'");
        }
        JsonObject legacy = result.getAsJsonObject();
        String item = legacy.has("item") ? legacy.get("item").getAsString()
                : legacy.has("id") ? legacy.get("id").getAsString() : null;
        if (item == null) {
            throw new RecipeConversionException("result has no item");
        }
        int data = legacy.has("data") ? legacy.get("data").getAsInt() : 0;
        String id = LegacyItemIds.legacyItemId(item, data);
        checkItem(id, itemExists, "result");
        JsonObject out = new JsonObject();
        out.addProperty("id", id);
        int count = legacy.has("count") ? legacy.get("count").getAsInt() : 1;
        out.addProperty("count", Math.max(1, count));
        if (legacy.has("nbt")) {
            ModLogger.debug("Recipe result NBT for '{}' is not supported and was dropped", id);
        }
        return out;
    }

    private static void checkItem(String id, @Nullable Predicate<ResourceLocation> itemExists, String what)
            throws RecipeConversionException {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new RecipeConversionException("invalid item id '" + id + "' in " + what);
        }
        if (itemExists != null && !itemExists.test(location)) {
            throw new RecipeConversionException("unknown item '" + id + "' in " + what);
        }
    }

    /** One ingredient: a list of alternatives, an ore name, a tag, a {@code #constant} or an item. */
    private static JsonElement ingredient(@Nullable JsonElement element, Map<String, JsonElement> constants,
                                          @Nullable Predicate<ResourceLocation> itemExists, int depth)
            throws RecipeConversionException {
        if (element == null || element.isJsonNull()) {
            throw new RecipeConversionException("missing ingredient");
        }
        if (depth > MAX_CONSTANT_DEPTH) {
            throw new RecipeConversionException("recursive ingredient constants");
        }
        if (element.isJsonArray()) {
            return alternatives(element.getAsJsonArray(), constants, itemExists, depth);
        }
        JsonObject legacy = (element.isJsonPrimitive() ? itemObject(element) : element).getAsJsonObject();
        String type = legacy.has("type") ? legacy.get("type").getAsString() : "";
        String name = withoutNamespace(type);
        if (name.equals("ore_dict") || legacy.has("ore")) {
            return RecipeJson.tag(LegacyItemIds.oreToTag(legacy.get("ore").getAsString()));
        }
        if (legacy.has("tag")) {
            JsonObject out = new JsonObject();
            out.add("tag", legacy.get("tag"));
            return out;
        }
        if (!name.isEmpty() && !name.equals("item") && !name.equals("item_nbt")) {
            throw new RecipeConversionException("unsupported ingredient type '" + type + "'");
        }
        return item(legacy, constants, itemExists, depth);
    }

    /** A list of ingredients, flattened; a list of one is that ingredient. */
    private static JsonElement alternatives(JsonArray list, Map<String, JsonElement> constants,
                                            @Nullable Predicate<ResourceLocation> itemExists, int depth)
            throws RecipeConversionException {
        JsonArray out = new JsonArray();
        for (JsonElement element : list) {
            JsonElement converted = ingredient(element, constants, itemExists, depth + 1);
            if (converted.isJsonArray()) {
                converted.getAsJsonArray().forEach(out::add);
            } else {
                out.add(converted);
            }
        }
        if (out.isEmpty()) {
            throw new RecipeConversionException("empty ingredient list");
        }
        return out.size() == 1 ? out.get(0) : out;
    }

    /** An item ingredient, or the constant it names with {@code #}; metadata 32767 (any) is read as 0. */
    private static JsonElement item(JsonObject legacy, Map<String, JsonElement> constants,
                                    @Nullable Predicate<ResourceLocation> itemExists, int depth)
            throws RecipeConversionException {
        if (!legacy.has("item")) {
            throw new RecipeConversionException("ingredient has no item");
        }
        String item = legacy.get("item").getAsString();
        if (item.startsWith("#")) {
            JsonElement constant = constants.get(item.substring(1));
            if (constant == null) {
                throw new RecipeConversionException("unknown recipe constant '" + item + "'");
            }
            return ingredient(constant, constants, itemExists, depth + 1);
        }
        int data = 0;
        if (legacy.has("data")) {
            data = legacy.get("data").getAsInt();
            if (data == Short.MAX_VALUE) {
                data = 0;
            }
        }
        String id = LegacyItemIds.legacyItemId(item, data);
        checkItem(id, itemExists, "ingredient");
        return RecipeJson.item(id);
    }

    private static JsonObject itemObject(JsonElement item) {
        JsonObject object = new JsonObject();
        object.add("item", item);
        return object;
    }

    private static String withoutNamespace(String type) {
        return type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
    }
}
