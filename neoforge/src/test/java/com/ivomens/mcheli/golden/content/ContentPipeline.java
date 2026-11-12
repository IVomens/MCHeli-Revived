package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.McheliMod;
import com.ivomens.mcheli.content.bind.UnsupportedSettings;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.content.source.FolderContentLoader;
import com.ivomens.mcheli.golden.MinecraftBootstrap;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runs content packs through the real parsers in the order {@code ContentRegistries.loadContents}
 * uses (HUDs, weapons, then the vehicle types and throwables), and fills each type's registry as the
 * game would, because later types look earlier ones up (a vehicle's weapons, a HUD call).
 * <p>
 * Unlike the game it also loads HUDs on this headless JVM, and it keeps every parsed entry by file
 * instead of only the last one per name, so each file gets its own dump.
 */
public final class ContentPipeline {

    /** Load order of {@code ContentRegistries.loadContents}. */
    private static final List<ContentType> STAGES = List.of(ContentType.HUD, ContentType.WEAPON,
            ContentType.HELICOPTER, ContentType.PLANE, ContentType.SHIP, ContentType.TANK, ContentType.VEHICLE,
            ContentType.THROWABLE);

    private static final String ASSETS = "assets/mcheli/";

    private ContentPipeline() {
    }

    /** The bundled content pack: {@code src/main/resources}. */
    public static Path bundledPack() {
        return Path.of(System.getProperty("mcheli.mainResources", "src/main/resources"));
    }

    /** A pack of test content below {@code src/test/resources/content/}. */
    public static Path testPack(String name) {
        Path resources = Path.of(System.getProperty("mcheli.testResources", "src/test/resources"));
        return resources.resolve("content").resolve(name);
    }

    /**
     * Loads the packs in order, as if they were installed addons, and returns the files the last
     * pack defines by their path inside that pack. Files that fail to parse are missing from the result.
     */
    public static Map<String, LoadedContent<?, ?>> load(Path... packs) {
        return loadEach(packs).get(packs[packs.length - 1]);
    }

    /** Like {@link #load}, but returns the parsed files of every pack, keyed by pack. */
    public static Map<Path, Map<String, LoadedContent<?, ?>>> loadEach(Path... packs) {
        MinecraftBootstrap.ensure();
        McheliMod.registerContentTypes();
        UnsupportedSettings.resetReports();
        Map<Path, Map<String, LoadedContent<?, ?>>> parsed = new LinkedHashMap<>();
        for (Path pack : packs) {
            parsed.put(pack, new LinkedHashMap<>());
        }
        for (ContentType type : STAGES) {
            List<LoadedContent<?, ?>> files = new ArrayList<>();
            for (Path pack : packs) {
                PackReader reader = new PackReader(pack);
                for (String file : contentFiles(pack, type)) {
                    LoadedContent<?, ?> content = reader.parse(file, type);
                    if (content != null) {
                        files.add(content);
                        parsed.get(pack).put(file, content);
                    }
                }
            }
            fill(ContentRegistries.registry(type), files);
        }
        return parsed;
    }

    private static <D extends ContentDefinition, T> void fill(ContentRegistry<D, T> registry,
                                                              List<LoadedContent<?, ?>> files) {
        registry.fill(files.stream().map(registry::cast).toList());
        registry.changed();
    }

    /**
     * Parses one file the way the game does (parse, then validate, errors logged and swallowed)
     * against whatever registries are installed. Returns null when the game would skip the file.
     */
    static LoadedContent<?, ?> parseOne(Path pack, String file, ContentType type) {
        UnsupportedSettings.resetReports();
        return new PackReader(pack).parse(file, type);
    }

    /** A pack-relative content path without its {@code assets/mcheli/} prefix: {@code weapons/aim9.yml}. */
    static String belowAssets(String file) {
        return file.startsWith(ASSETS) ? file.substring(ASSETS.length()) : file;
    }

    /** Content files of one type inside a pack, sorted, as pack-relative paths. */
    static List<String> contentFiles(Path pack, ContentType type) {
        Path dir = pack.resolve("assets/mcheli").resolve(type.dirName);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> pack.relativize(file).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads files through the game's own loader, so parse errors are logged exactly as in game. */
    private static final class PackReader extends FolderContentLoader {

        PackReader(Path pack) {
            super("mcheli", pack.toFile(), "1", path -> true);
        }

        LoadedContent<?, ?> parse(String file, ContentType type) {
            try {
                return this.makeEntry(file, type, false).parse();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
