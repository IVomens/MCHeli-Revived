package com.ivomens.mcheli.content.pack;

import com.ivomens.mcheli.api.content.ContentPackProvider;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * The content packs other mods ship in their jars, registered through the API's {@link ContentPackProvider}.
 * Each start-up copies them into {@code mcheli_mod_packs/<pack>} in the game directory, a folder this class
 * owns and empties first, so they load through the same pipeline, override rules, resource packs and
 * reloads as the packs in {@code mcheli_addons/}. {@link PackOrder} puts them after the bundled content and
 * before the player's own packs.
 */
public final class ModContentPacks {

    /** The folder in the game directory the mods' packs are copied to. */
    public static final String FOLDER_NAME = "mcheli_mod_packs";

    private ModContentPacks() {
    }

    /** A pack one mod registered: a folder among its files. */
    record Registration(String modId, String folder) {
    }

    /**
     * Asks every provider for its packs, copies them into {@code mcheli_mod_packs/} next to {@code addonDir} and
     * returns the copies in load order: by mod id, then in the order each mod registered them.
     */
    public static List<File> extract(File addonDir) {
        List<Registration> packs = registered();
        File target = new File(addonDir.getAbsoluteFile().getParentFile(), FOLDER_NAME);
        try {
            deleteRecursively(target.toPath());
        } catch (IOException e) {
            ModLogger.error("Could not clear the mod content pack folder '{}'", target.getAbsolutePath(), e);
            return List.of();
        }
        if (packs.isEmpty()) {
            return List.of();
        }
        List<File> extracted = new ArrayList<>();
        Map<String, Integer> perMod = new HashMap<>();
        for (Registration pack : packs) {
            int index = perMod.merge(pack.modId(), 1, Integer::sum);
            String name = folderName(pack, index);
            File folder = copy(pack, new File(target, name), name);
            if (folder != null) {
                extracted.add(folder);
            }
        }
        ModLogger.info("Found {} content packs in other mods: {}", extracted.size(), packs);
        return extracted;
    }

    /** The registrations of every provider on the class path, sorted by mod id. A broken provider is skipped. */
    static List<Registration> registered() {
        List<Registration> packs = new ArrayList<>();
        Iterator<ContentPackProvider> providers =
                ServiceLoader.load(ContentPackProvider.class, ModContentPacks.class.getClassLoader()).iterator();
        while (true) {
            ContentPackProvider provider;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                provider = providers.next();
            } catch (ServiceConfigurationError e) {
                ModLogger.error("A content pack provider could not be created", e);
                continue;
            }
            try {
                provider.registerPacks((modId, folder) -> packs.add(new Registration(modId, folder)));
            } catch (RuntimeException e) {
                ModLogger.error("Content pack provider " + provider.getClass().getName() + " failed", e);
            }
        }
        packs.sort(Comparator.comparing(Registration::modId));
        return packs;
    }

    /** The mod id for a mod's first pack, then the mod id and the folder's name. */
    static String folderName(Registration pack, int indexInMod) {
        String name = indexInMod == 1 ? pack.modId() : pack.modId() + "." + pack.folder();
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private static File copy(Registration pack, File folder, String domain) {
        Path source = Services.PLATFORM.findModFile(pack.modId(), pack.folder());
        if (source == null || !Files.isDirectory(source)) {
            ModLogger.warn("Mod '{}' registered the content pack folder '{}', which {}; skipping it", pack.modId(),
                    pack.folder(), Services.PLATFORM.isModLoaded(pack.modId()) ? "does not exist" : "is not loaded");
            return null;
        }
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path destination = folder.toPath();
                for (Path part : source.relativize(file)) {
                    destination = destination.resolve(stripSlash(part.toString()));
                }
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination);
            }
            Path packMeta = folder.toPath().resolve("pack.mcmeta");
            if (!Files.exists(packMeta)) {
                Files.createDirectories(folder.toPath());
                Files.writeString(packMeta, defaultPackMeta(pack.modId(), domain), StandardCharsets.UTF_8);
            }
            return folder;
        } catch (IOException e) {
            ModLogger.error("Could not copy the content pack '" + pack.folder() + "' of mod '" + pack.modId() + "'", e);
            return null;
        }
    }

    /** Metadata for a pack that brings none: its domain is the folder's name, and the whole pack is read. */
    private static String defaultPackMeta(String modId, String domain) {
        return """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "MCHeli content of %s"
                  },
                  "addon": {
                    "domain": "%s",
                    "loader_version": "2"
                  }
                }
                """.formatted(modId, domain);
    }

    private static String stripSlash(String name) {
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
