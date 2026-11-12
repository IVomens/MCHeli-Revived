package com.ivomens.mcheli.content.pack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.ivomens.mcheli.content.recipe.GeneratedRecipes;
import com.ivomens.mcheli.content.recipe.RecipeJson;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class AddonManager {

    public static final Pattern ZIP_PATTERN = Pattern.compile("(.+)\\.(zip|jar)$");
    public static final String BUILTIN_ADDON_DOMAIN = "@builtin";
    public static final String ADDON_FOLDER_NAME = "mcheli_addons";
    private static final Map<String, AddonPack> ADDON_LIST = Maps.newLinkedHashMap();

    @Nullable
    public static AddonPack get(String addonDomain) {
        return BUILTIN_ADDON_DOMAIN.equals(addonDomain) ? BuiltinAddonPack.instance() : ADDON_LIST.get(addonDomain);
    }

    public static List<AddonPack> getLoadedAddons() {
        return ImmutableList.copyOf(ADDON_LIST.values());
    }

    /**
     * The {@code mcheli_addons} folder in the game directory.
     */
    public static File getAddonDir() {
        return Services.PLATFORM.getGameDir().resolve(ADDON_FOLDER_NAME).toFile();
    }

    public static List<AddonPack> loadAddons(File addonDir) {
        ModLogger.info("Loading content from: " + addonDir.getAbsolutePath());
        checkExistAddonDir(addonDir);
        List<AddonPack> addons = Lists.newArrayList();
        File[] addonFiles = addonDir.listFiles();
        if (addonFiles == null) {
            addonFiles = new File[0];
        }
        Arrays.sort(addonFiles, PackOrder.LOAD_ORDER);

        ModLogger.info("Found contents: " + Arrays.toString(addonFiles));
        for (File addonFile : PackOrder.withModPacks(addonFiles, ModContentPacks.extract(addonDir))) {
            if (validAddonPath(addonFile)) {
                AddonPack data = loadAddon(addonFile);
                if (data != null) {
                    addons.add(data);
                }
            }
        }

        checkExistAddonDir(GeneratedAddonPack.instance().getFile());
        ModLogger.info("Loaded {} addon packs", addons.size());
        return addons;
    }

    @Nullable
    private static AddonPack loadAddon(File addonFile) {
        try {
            AddonPack addonPack = AddonPack.create(addonFile);
            AddonPack sameDomain = ADDON_LIST.put(addonPack.getDomain(), addonPack);
            if (sameDomain != null && !sameDomain.getFile().equals(addonFile)) {
                ModLogger.warn("Addon packs '{}' and '{}' both use the domain '{}'; give each pack its own, or an "
                        + "in-game reload reads the wrong pack's files", sameDomain.getFile().getName(),
                        addonFile.getName(), addonPack.getDomain());
            }
            return addonPack;
        } catch (Exception e) {
            ModLogger.error("Failed to load addon pack '{}'", addonFile.getName(), e);
            return null;
        }
    }

    private static boolean validAddonPath(File addonFile) {
        return !GeneratedAddonPack.isGeneratedAddonName(addonFile) &&
                (addonFile.isDirectory() || ZIP_PATTERN.matcher(addonFile.getName()).matches());
    }

    private static void checkExistAddonDir(File addonDir) {
        if (!addonDir.exists()) {
            addonDir.mkdirs();
        }
    }

    // Resource / data packs

    /**
     * Adds one pack per loaded addon: client resources (textures, models, sounds, lang) and server data
     * (the addon's own data files plus its legacy recipes converted to 1.21 recipes). Also adds the
     * generated addon folder (client) and the generated recipe pack (server). The loader adds this source
     * to its client resource packs and to its server data packs.
     */
    public static RepositorySource packSource(PackType type) {
        return consumer -> {
            for (AddonPack addon : getLoadedAddons()) {
                Pack pack = createAddonPack(addon, type);
                if (pack != null) {
                    consumer.accept(pack);
                }
            }
            if (type == PackType.CLIENT_RESOURCES) {
                Pack generated = createAddonPack(GeneratedAddonPack.instance(), type);
                if (generated != null) {
                    consumer.accept(generated);
                }
            } else {
                consumer.accept(createGeneratedRecipePack());
            }
        };
    }

    @Nullable
    private static Pack createAddonPack(AddonPack addon, PackType type) {
        File file = addon.getFile();
        if (file == null || !file.exists()) {
            return null;
        }
        String id = "mcheli_addon/" + addon.getDomain() + (type == PackType.SERVER_DATA ? "/data" : "");
        PackLocationInfo info = new PackLocationInfo(id, Component.literal("MCHeli addon: " + addon.getName()),
                PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier base = file.isDirectory()
                ? new PathPackResources.PathResourcesSupplier(file.toPath())
                : new FilePackResources.FileResourcesSupplier(file);
        Pack.ResourcesSupplier supplier = type == PackType.SERVER_DATA
                ? new AddonDataPackResources.Supplier(base, addon.getDomain())
                : base;
        Pack.Metadata metadata = Services.PACKS.hiddenPackMetadata(Component.literal(addon.getName()));
        return new Pack(info, supplier, metadata, new PackSelectionConfig(true, Pack.Position.TOP, false));
    }

    private static Pack createGeneratedRecipePack() {
        PackLocationInfo info = new PackLocationInfo("mcheli_generated_recipes", Component.literal("MCHeli generated recipes"),
                PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return open(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return open(location);
            }

            private PackResources open(PackLocationInfo location) {
                Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, JsonObject> e : GeneratedRecipes.snapshot().entrySet()) {
                    ResourceLocation id = e.getKey();
                    files.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json"),
                            RecipeJson.toJsonString(e.getValue()).getBytes(StandardCharsets.UTF_8));
                }
                return new MemoryPackResources(location, PackType.SERVER_DATA, files);
            }
        };
        Pack.Metadata metadata = Services.PACKS.hiddenPackMetadata(Component.literal("MCHeli generated recipes"));
        return new Pack(info, supplier, metadata, new PackSelectionConfig(true, Pack.Position.TOP, false));
    }
}
