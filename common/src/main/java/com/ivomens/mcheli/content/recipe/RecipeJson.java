package com.ivomens.mcheli.content.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Writes crafting recipes, their ingredients and results as 1.21 recipe JSON. */
public final class RecipeJson {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RecipeJson() {
    }

    /**
     * A shaped crafting recipe in the style of the old {@code CraftingHelper.parseShaped}: pattern rows
     * ({@code String} or {@code String[]}) followed by pairs of {@code Character} and ingredient. Ingredients
     * may be anything {@link #ingredient} accepts. Rows shorter than the longest are padded with spaces.
     */
    public static JsonObject shaped(ItemStack output, Object... params) {
        List<String> pattern = new ArrayList<>();
        int keysStart = readPattern(params, pattern);
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Shaped recipe without pattern");
        }
        JsonObject key = readKeys(params, keysStart);
        checkPatternKeys(pattern, key);

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.add("pattern", paddedRows(pattern));
        json.add("key", key);
        json.add("result", result(output));
        return json;
    }

    /** Adds the leading pattern rows of {@code params} to {@code pattern}; returns the index after them. */
    private static int readPattern(Object[] params, List<String> pattern) {
        int index = 0;
        while (index < params.length) {
            if (params[index] instanceof String[] rows) {
                Collections.addAll(pattern, rows);
            } else if (params[index] instanceof String row) {
                pattern.add(row);
            } else {
                break;
            }
            index++;
        }
        return index;
    }

    private static JsonArray paddedRows(List<String> pattern) {
        int width = 0;
        for (String row : pattern) {
            width = Math.max(width, row.length());
        }
        JsonArray rows = new JsonArray();
        for (String row : pattern) {
            StringBuilder sb = new StringBuilder(row);
            while (sb.length() < width) {
                sb.append(' ');
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    /** The {@code Character}, ingredient pairs from {@code start} on, as the recipe's {@code key} object. */
    private static JsonObject readKeys(Object[] params, int start) {
        JsonObject key = new JsonObject();
        for (int index = start; index + 1 < params.length; index += 2) {
            if (!(params[index] instanceof Character symbol)) {
                throw new IllegalArgumentException("Invalid shaped recipe key: " + params[index]);
            }
            key.add(String.valueOf(symbol), ingredient(params[index + 1]));
        }
        return key;
    }

    private static void checkPatternKeys(List<String> pattern, JsonObject key) {
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ' && !key.has(String.valueOf(c))) {
                    throw new IllegalArgumentException("Pattern references undefined key '" + c + "'");
                }
            }
        }
    }

    /** The {@code result} object of a recipe making {@code stack}. */
    public static JsonObject result(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Recipe result is empty");
        }
        JsonObject result = new JsonObject();
        result.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", Math.max(1, stack.getCount()));
        return result;
    }

    /**
     * One ingredient: an {@link Ingredient}, {@link ItemStack}, {@link ItemLike}, {@link TagKey}, a
     * {@code #tag} string, an ore dictionary name (converted to the matching common tag) or an item id, which
     * may be a 1.12 name.
     */
    public static JsonElement ingredient(Object ingredient) {
        return switch (ingredient) {
            case Ingredient value -> Ingredient.CODEC_NONEMPTY.encodeStart(JsonOps.INSTANCE, value)
                    .getOrThrow(IllegalArgumentException::new);
            case ItemStack stack -> {
                if (stack.isEmpty()) {
                    throw new IllegalArgumentException("Empty ingredient");
                }
                yield item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
            case ItemLike like -> item(BuiltInRegistries.ITEM.getKey(like.asItem()).toString());
            case TagKey<?> tag -> tag(tag.location().toString());
            case String text -> textIngredient(text);
            default -> throw new IllegalArgumentException("Unsupported ingredient: " + ingredient);
        };
    }

    private static JsonObject textIngredient(String text) {
        if (text.startsWith("#")) {
            return tag(text.substring(1));
        }
        if (isOreName(text)) {
            return tag(LegacyItemIds.oreToTag(text));
        }
        return item(LegacyItemIds.legacyItemId(text, 0));
    }

    /** Ore dictionary names are camel case without a namespace, such as {@code ingotIron}. */
    private static boolean isOreName(String text) {
        return text.indexOf(':') < 0 && !text.isEmpty() && Character.isLowerCase(text.charAt(0))
                && text.chars().anyMatch(Character::isUpperCase);
    }

    static JsonObject item(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    static JsonObject tag(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("tag", id);
        return json;
    }

    public static String toJsonString(JsonElement element) {
        return GSON.toJson(element);
    }
}
