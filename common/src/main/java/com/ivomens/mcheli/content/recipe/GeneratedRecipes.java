package com.ivomens.mcheli.content.recipe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Recipes built in code rather than shipped as data pack JSON: the vehicle and throwable recipes of the
 * content files. 1.21 recipes are data pack files loaded by the server's recipe manager, so the recipes kept
 * here are served by the "MCHeli generated recipes" data pack (see {@code AddonManager}) and take effect on
 * the next data pack (re)load.
 */
public final class GeneratedRecipes {

    private static final Map<ResourceLocation, JsonObject> GENERATED =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private GeneratedRecipes() {
    }

    /** Adds (or replaces) a generated recipe in 1.21 JSON form. */
    public static ResourceLocation register(String name, JsonObject recipe) {
        ResourceLocation id = toId(name);
        GENERATED.put(id, recipe);
        return id;
    }

    /**
     * Adds a generated recipe under a unique id derived from {@code name} ({@code name}, {@code name_2},
     * {@code name_3} ...), so several recipes for the same item do not overwrite each other.
     */
    public static ResourceLocation registerUnique(String name, JsonObject recipe) {
        synchronized (GENERATED) {
            ResourceLocation id = toId(name);
            int n = 2;
            while (GENERATED.containsKey(id)) {
                id = toId(name + "_" + n++);
            }
            GENERATED.put(id, recipe);
            return id;
        }
    }

    public static void unregister(ResourceLocation id) {
        GENERATED.remove(id);
    }

    public static void clear() {
        GENERATED.clear();
    }

    /** Snapshot of all generated recipes (id -> 1.21 recipe JSON). */
    public static Map<ResourceLocation, JsonObject> snapshot() {
        synchronized (GENERATED) {
            return ImmutableMap.copyOf(GENERATED);
        }
    }

    /**
     * Adds a shaped crafting recipe; see {@link RecipeJson#shaped} for the parameters.
     *
     * @return the id of the generated recipe
     */
    public static ResourceLocation addShaped(String name, ItemStack output, Object... params) {
        return registerUnique(name, RecipeJson.shaped(output, params));
    }

    /**
     * Adds a shapeless crafting recipe; see {@link RecipeJson#ingredient} for the accepted ingredients.
     *
     * @return the id of the generated recipe
     */
    public static ResourceLocation addShapeless(String name, ItemStack output, Object... ingredients) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        JsonArray array = new JsonArray();
        for (Object ingredient : ingredients) {
            array.add(RecipeJson.ingredient(ingredient));
        }
        if (array.isEmpty()) {
            throw new IllegalArgumentException("Shapeless recipe without ingredients: " + name);
        }
        json.add("ingredients", array);
        json.add("result", RecipeJson.result(output));
        return registerUnique(name, json);
    }

    private static ResourceLocation toId(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.indexOf(':') >= 0) {
            ResourceLocation id = ResourceLocation.tryParse(lower);
            if (id != null) {
                return id;
            }
        }
        return Ids.of(sanitizePath(lower));
    }

    private static String sanitizePath(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            sb.append(ResourceLocation.validPathChar(c) ? c : '_');
        }
        return sb.toString();
    }
}
