package com.ivomens.mcheli.content.pack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.ivomens.mcheli.config.LoadingSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BuiltinAddonPack extends AddonPack {

    public static final String EXTRACTED_FOLDER_NAME = "default";
    /** Marker file holding the mod version the folder was extracted from. */
    private static final String VERSION_FILE = ".mcheli_version";
    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 34,
                "description": "MCHeli default content pack"
              },
              "addon": {
                "domain": "@builtin",
                "version": "1.0",
                "loader_version": "2",
                "credits": "EMB4-MCHeli",
                "authors": ["EMB4", "Murachiki27"],
                "description": "Builtin addon extracted to disk"
              }
            }
            """;
    private static final BuiltinAddonPack INSTANCE = new BuiltinAddonPack();

    private BuiltinAddonPack() {
        super(AddonManager.BUILTIN_ADDON_DOMAIN, "MCHeli-Builtin", "1.0", null, "EMB4-MCHeli", ImmutableList.of("EMB4", "Murachiki27"),
                "Builtin addon", "1", ImmutableMap.of());
    }

    public static BuiltinAddonPack instance() {
        return INSTANCE;
    }

    @Override
    public File getFile() {
        return new File(AddonManager.getAddonDir(), EXTRACTED_FOLDER_NAME);
    }

    /**
     * Extracts the builtin content (the mod's {@code assets/mcheli}) into {@code mcheli_addons/default}
     * unless it was already extracted. The extracted copy is refreshed when the mod version changes, so
     * an updated mod does not keep loading stale default content.
     *
     * @return true when the config changed and should be saved
     */
    public boolean ensureExtracted() {
        File targetDir = this.getFile();
        String currentVersion = getModVersion();
        if (McheliConfig.loading().extractDefaultContentPack() && targetDir.exists()
                && currentVersion.equals(readExtractedVersion(targetDir))) {
            return false;
        }

        try {
            extractTo(targetDir);
            writeExtractedVersion(targetDir, currentVersion);
            McheliConfig.update(config -> config.with(LoadingSettings.EXTRACT_DEFAULT_CONTENT_PACK, true));
            return true;
        } catch (IOException e) {
            ModLogger.error("Failed to extract builtin content pack to '{}'", targetDir.getAbsolutePath(), e);
            return false;
        }
    }

    private void extractTo(File targetDir) throws IOException {
        deleteRecursively(targetDir);
        if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException("Failed to create builtin addon dir: " + targetDir.getAbsolutePath());
        }

        writePackMeta(targetDir);
        int copied = 0;
        Path modRoot = Services.PLATFORM.getModRoot();
        if (modRoot == null) {
            throw new IOException("Mod resources are not available");
        }
        try (ResourceLoader loader = ResourceLoader.create(modRoot)) {
            for (ResourceLoader.ResourceEntry entry : loader.loadAll(resource ->
                    !resource.isDirectory() && resource.getPath().startsWith("assets/mcheli/"))) {
                copyEntry(loader, entry, targetDir);
                copied++;
            }
        }

        ModLogger.get().info("Extracted {} builtin content files to '{}'", copied, targetDir.getAbsolutePath());
    }

    private void writePackMeta(File targetDir) throws IOException {
        File packMeta = new File(targetDir, "pack.mcmeta");
        try (OutputStream out = new FileOutputStream(packMeta)) {
            out.write(PACK_META.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void copyEntry(ResourceLoader loader, ResourceLoader.ResourceEntry entry, File targetDir) throws IOException {
        File outFile = new File(targetDir, entry.getPath());
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
        }

        try (InputStream in = loader.getInputStreamFromEntry(entry);
             OutputStream out = new FileOutputStream(outFile)) {
            in.transferTo(out);
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath());
        }
    }

    private static String getModVersion() {
        return Services.PLATFORM.getModVersion();
    }

    private static String readExtractedVersion(File targetDir) {
        File versionFile = new File(targetDir, VERSION_FILE);
        if (!versionFile.isFile()) {
            return "";
        }
        try {
            return Files.readString(versionFile.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeExtractedVersion(File targetDir, String version) {
        try {
            Files.write(new File(targetDir, VERSION_FILE).toPath(),
                    version.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            ModLogger.warn("Failed to write builtin content version marker", e);
        }
    }
}
