package com.ivomens.mcheli.content.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Converts the legacy 1.12 recipe JSON of a pack ({@code assets/<ns>/recipes/*.json}, with the shared
 * {@code _constants.json}) into 1.21 data pack recipes ({@code data/<ns>/recipe/*.json}). Every recipe that
 * fails to convert, or that names an item which does not exist, is skipped with a warning naming the file and
 * the reason.
 */
public final class LegacyRecipePacks {

    private static final String RECIPES_FOLDER = "recipes/";

    private LegacyRecipePacks() {
    }

    /**
     * Converts every legacy recipe found under {@code assets/<ns>/recipes} of {@code pack}.
     *
     * @param pack       the pack to read (read with {@link PackType#CLIENT_RESOURCES})
     * @param sourceName name used in warnings (addon domain)
     * @param itemExists item id validator; recipes referencing unknown items are skipped with a warning
     * @return converted recipes keyed by their data pack location ({@code ns:recipe/<name>.json})
     */
    public static Map<ResourceLocation, byte[]> convert(PackResources pack, String sourceName,
                                                        @Nullable Predicate<ResourceLocation> itemExists) {
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        int converted = 0;
        int skipped = 0;
        for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
            Map<String, JsonElement> constants = readConstants(pack, ns, sourceName);
            Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
            pack.listResources(PackType.CLIENT_RESOURCES, ns, "recipes", files::put);
            for (Map.Entry<ResourceLocation, IoSupplier<InputStream>> file : files.entrySet()) {
                switch (convertFile(ns, file.getKey().getPath(), file.getValue(), constants, itemExists, sourceName,
                        out)) {
                    case CONVERTED -> converted++;
                    case SKIPPED -> skipped++;
                    case NOT_A_RECIPE -> {
                    }
                }
            }
        }
        if (converted > 0 || skipped > 0) {
            ModLogger.info("Converted {} legacy recipes from pack '{}' ({} skipped)", converted, sourceName, skipped);
        }
        return out;
    }

    /** What became of one file of the recipes folder. */
    private enum Outcome {
        CONVERTED,
        SKIPPED,
        /** Not JSON, or a file of shared definitions such as {@code _constants.json}. */
        NOT_A_RECIPE
    }

    /** Converts one recipe file into {@code out}. */
    private static Outcome convertFile(String ns, String path, IoSupplier<InputStream> file,
                                       Map<String, JsonElement> constants,
                                       @Nullable Predicate<ResourceLocation> itemExists, String sourceName,
                                       Map<ResourceLocation, byte[]> out) {
        if (!path.endsWith(".json")) {
            return Outcome.NOT_A_RECIPE;
        }
        String relative = path.substring(RECIPES_FOLDER.length());
        String fileName = relative.substring(relative.lastIndexOf('/') + 1);
        if (fileName.startsWith("_")) {
            return Outcome.NOT_A_RECIPE;
        }
        String where = "assets/" + ns + "/" + path + " (pack '" + sourceName + "')";
        try (Reader reader = new InputStreamReader(file.get(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new RecipeConversionException("not a json object");
            }
            JsonObject converted = LegacyRecipeConverter.convert(parsed.getAsJsonObject(), constants, itemExists);
            ResourceLocation target = ResourceLocation.fromNamespaceAndPath(ns, "recipe/" + relative);
            out.put(target, RecipeJson.toJsonString(converted).getBytes(StandardCharsets.UTF_8));
            return Outcome.CONVERTED;
        } catch (RecipeConversionException ex) {
            ModLogger.warn("Skipping recipe {}: {}", where, ex.getMessage());
        } catch (Exception ex) {
            ModLogger.warn("Skipping recipe {}: failed to read or parse ({})", where, ex.toString());
        }
        return Outcome.SKIPPED;
    }

    /** The named ingredients of {@code assets/<ns>/recipes/_constants.json}, which recipes use as {@code #name}. */
    private static Map<String, JsonElement> readConstants(PackResources pack, String ns, String sourceName) {
        Map<String, JsonElement> constants = new HashMap<>();
        ResourceLocation location = ResourceLocation.tryBuild(ns, RECIPES_FOLDER + "_constants.json");
        if (location == null) {
            return constants;
        }
        IoSupplier<InputStream> file = pack.getResource(PackType.CLIENT_RESOURCES, location);
        if (file == null) {
            return constants;
        }
        try (Reader reader = new InputStreamReader(file.get(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        JsonObject constant = element.getAsJsonObject();
                        if (constant.has("name") && constant.has("ingredient")) {
                            constants.put(constant.get("name").getAsString(), constant.get("ingredient"));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ModLogger.warn("Failed to read recipe constants assets/{}/recipes/_constants.json (pack '{}'): {}", ns,
                    sourceName, ex.toString());
        }
        return constants;
    }
}
