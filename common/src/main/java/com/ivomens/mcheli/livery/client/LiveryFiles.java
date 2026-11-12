package com.ivomens.mcheli.livery.client;

import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** The player's livery folder, {@code <gameDir>/mcheli_liveries}: images to upload, templates, cache. */
final class LiveryFiles {

    private static final int MAX_LISTED_FILES = 200;

    private LiveryFiles() {
    }

    static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("mcheli_liveries");
    }

    static Path templates() {
        return root().resolve("templates");
    }

    static boolean isPng(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    /** An image in the livery folder. */
    record LocalImage(Path path, long size) {
    }

    /** PNG files directly in the livery folder, by name. Does disk IO; call off the render thread. */
    static List<LocalImage> listImages() {
        Path root = root();
        try {
            Files.createDirectories(root);
            try (Stream<Path> files = Files.list(root)) {
                return files.filter(file -> Files.isRegularFile(file) && isPng(file))
                        .sorted(Comparator.comparing(file -> file.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .limit(MAX_LISTED_FILES)
                        .map(file -> new LocalImage(file, sizeOf(file)))
                        .toList();
            }
        } catch (IOException e) {
            ModLogger.warn("Could not list {}: {}", root, e.getMessage());
            return new ArrayList<>();
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Copies a file dropped on the screen into the livery folder, so it shows up in the list. */
    static Path importFile(Path file) throws IOException {
        Path root = root();
        Files.createDirectories(root);
        Path target = root.resolve(file.getFileName().toString());
        if (!Files.exists(target) || !Files.isSameFile(file, target)) {
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    static void open(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            ModLogger.warn("Could not create {}: {}", directory, e.getMessage());
        }
        Util.getPlatform().openFile(directory.toFile());
    }
}
