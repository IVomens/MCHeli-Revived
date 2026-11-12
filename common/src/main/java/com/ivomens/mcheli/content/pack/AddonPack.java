package com.ivomens.mcheli.content.pack;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.util.GsonHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One addon in {@code mcheli_addons/}: a folder or a zip/jar file with a {@code pack.mcmeta}. The optional
 * {@code "addon"} object of that file sets the domain, version, authors and {@code loader_version}
 * ("1" scans {@code assets/} only, "2" scans the whole pack; packs without metadata default to "2").
 */
public class AddonPack {

    private final String addonDomain;
    private final String addonName;
    private final String addonVersion;
    protected final ImmutableMap<String, JsonElement> packMetaMap;
    private final File addonFile;
    private final String credits;
    private final List<String> authors;
    private final String description;
    private final String loaderVersion;

    public AddonPack(
                     String addonDomain,
                     String addonName,
                     String addonVersion,
                     File addonFile,
                     String credits,
                     List<String> authors,
                     String description,
                     String loaderVersion,
                     ImmutableMap<String, JsonElement> packMetaMap) {
        this.addonDomain = addonDomain;
        this.addonName = addonName;
        this.addonVersion = addonVersion;
        this.addonFile = addonFile;
        this.credits = credits;
        this.authors = authors;
        this.description = description;
        this.loaderVersion = loaderVersion;
        this.packMetaMap = packMetaMap;
    }

    public static AddonPack create(File addonFile) {
        JsonObject packMetaJson = loadPackMeta(addonFile);
        JsonObject packJson = GsonHelper.getAsJsonObject(packMetaJson, "pack", new JsonObject());
        JsonObject addonJson = GsonHelper.getAsJsonObject(packMetaJson, "addon", new JsonObject());
        boolean hasPackMeta = !packMetaJson.entrySet().isEmpty();
        String addonDomain = getAddonDomain(addonJson, addonFile);
        String packName = GsonHelper.getAsString(packJson, "description", stripExtension(addonFile.getName()));
        String version = GsonHelper.getAsString(addonJson, "version", "0.0");

        String credits = GsonHelper.getAsString(addonJson, "credits", "");
        String description = GsonHelper.getAsString(addonJson, "description", "");
        String loaderVersion = GsonHelper.getAsString(addonJson, "loader_version", hasPackMeta ? "1" : "2");
        List<String> authors = getAuthors(addonJson);
        return new AddonPack(
                addonDomain, packName, version, addonFile, credits, authors, description, loaderVersion,
                ImmutableMap.copyOf(packMetaJson.entrySet()));
    }

    private static String getAddonDomain(JsonObject addonJson, File addonFile) {
        String configured = GsonHelper.getAsString(addonJson, "domain", "");
        if (!configured.isEmpty()) {
            return configured.toLowerCase(Locale.ROOT);
        }

        String fallback = stripExtension(addonFile.getName()).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (fallback.isEmpty()) {
            fallback = "addon_" + Integer.toHexString(addonFile.getName().hashCode());
        }

        ModLogger.get().warn("Addon '{}' has no domain metadata, using fallback domain '{}'",
                addonFile.getName(), fallback);
        return fallback;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static List<String> getAuthors(JsonObject jsonObject) {
        List<String> list = new ArrayList<>();
        if (jsonObject.has("authors")) {
            JsonElement authors = jsonObject.get("authors");
            if (authors.isJsonArray()) {
                for (JsonElement author : authors.getAsJsonArray()) {
                    list.add(author.getAsString());
                }
            }
        } else if (jsonObject.has("author")) {
            JsonElement author = jsonObject.get("author");
            if (author.isJsonPrimitive()) {
                list.add(author.getAsString());
            }
        }
        return list;
    }

    private static JsonObject loadPackMeta(File addonFile) {
        try (ResourceLoader loader = ResourceLoader.create(addonFile);
             BufferedReader bufferedReader = new BufferedReader(
                     new InputStreamReader(loader.getInputStream("pack.mcmeta"), StandardCharsets.UTF_8))) {
            JsonElement element = JsonParser.parseReader(bufferedReader);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            ModLogger.get().warn("'pack.mcmeta' in '{}' is not a json object", addonFile.getName());
        } catch (FileNotFoundException ignored) {
            ModLogger.get().warn("No 'pack.mcmeta' found in '{}'", addonFile.getName());
        } catch (Exception e) {
            ModLogger.get().error("Failed to read 'pack.mcmeta' in '{}'", addonFile.getName(), e);
        }

        return new JsonObject();
    }

    public String getDomain() {
        return this.addonDomain;
    }

    public String getName() {
        return this.addonName;
    }

    public String getVersion() {
        return this.addonVersion;
    }

    public String getAuthorsString() {
        return Joiner.on(", ").join(this.authors);
    }

    public String getCredits() {
        return this.credits;
    }

    public String getDescription() {
        return this.description;
    }

    public String getLoaderVersion() {
        return this.loaderVersion;
    }

    public File getFile() {
        return this.addonFile;
    }

    public ImmutableMap<String, JsonElement> getPackMetaMap() {
        return this.packMetaMap;
    }
}
