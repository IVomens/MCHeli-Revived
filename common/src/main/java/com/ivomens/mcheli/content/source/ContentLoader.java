package com.ivomens.mcheli.content.source;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.ivomens.mcheli.content.pack.AddonResourceLocation;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.report.ReportCollector;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Finds and reads the content files of one addon pack. Subclasses handle the storage: a plain folder
 * ({@link FolderContentLoader}) or a zip/jar archive ({@link FileContentLoader}).
 *
 * <p>Content files live in {@code assets/mcheli/<type folder>/<name>.<ext>}; see {@link ContentType} for the
 * folder names and {@link ContentFormat} for the extensions. Packs with {@code loader_version} "1" (the
 * default when a {@code pack.mcmeta} is present) are only searched below {@code assets/}, version "2" packs
 * are searched everywhere.
 */
public abstract class ContentLoader {

    /** {@code loader_version} that enables a search through the whole pack. */
    private static final String RECURSIVE_LOADER_VERSION = "2";
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    protected final String domain;
    protected final File dir;
    protected final String loaderVersion;
    private final Predicate<String> fileFilter;

    public ContentLoader(String domain, File addonDir, String loaderVersion, Predicate<String> fileFilter) {
        this.domain = domain;
        this.dir = addonDir;
        this.loaderVersion = loaderVersion;
        this.fileFilter = fileFilter;
    }

    public boolean isReadable(String path) {
        return this.fileFilter.test(path);
    }

    protected boolean searchesWholePack() {
        return RECURSIVE_LOADER_VERSION.equals(this.loaderVersion);
    }

    /** Reads every content file in the pack, grouped by content type. Nothing is parsed yet. */
    public Multimap<ContentType, PackFile> load() {
        Multimap<ContentType, PackFile> map = LinkedHashMultimap.create();
        for (PackFile file : this.getEntries()) {
            map.put(file.getType(), file);
        }
        return map;
    }

    protected abstract List<PackFile> getEntries();

    protected abstract InputStream getInputStreamByName(String name) throws IOException;

    /**
     * Reads the files behind {@code oldContents} again. Files that fail to parse keep their old entry, so a
     * typo made while editing a pack in game does not make a vehicle disappear.
     */
    public <D extends ContentDefinition, T> List<LoadedContent<D, T>> reread(ContentRegistry<D, T> registry,
                                                                             List<LoadedContent<D, T>> oldContents) {
        List<LoadedContent<D, T>> list = new ArrayList<>();
        for (LoadedContent<D, T> old : oldContents) {
            list.add(reread(registry, old));
        }
        return list;
    }

    /** Reads the file behind {@code old} again and returns {@code old} when that fails. */
    public <D extends ContentDefinition, T> LoadedContent<D, T> reread(ContentRegistry<D, T> registry,
                                                                       LoadedContent<D, T> old) {
        try {
            LoadedContent<?, ?> content = this.makeEntry(old.entry().filePath(), registry.type(), true).parse();
            if (content == null) {
                return old;
            }
            LoadedContent<D, T> reloaded = registry.cast(content);
            registry.reloaded(reloaded);
            return reloaded;
        } catch (IOException e) {
            ModLogger.get().error("Could not read '{}' from addon '{}'", old.entry().filePath(), this.domain, e);
            return old;
        }
    }

    protected PackFile makeEntry(String filepath, ContentType type, boolean reload) throws IOException {
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.getInputStreamByName(filepath), StandardCharsets.UTF_8))) {
            lines = reader.lines().collect(Collectors.toList());
        }
        // Windows editors start UTF-8 files with a byte order mark; it is not part of the first key
        if (!lines.isEmpty() && lines.getFirst().startsWith(BYTE_ORDER_MARK)) {
            lines.set(0, lines.getFirst().substring(BYTE_ORDER_MARK.length()));
        }
        return new PackFile(filepath, this.domain, type, lines, reload);
    }

    /** One content file that has been read into memory and is waiting to be parsed. */
    public static class PackFile {

        private final String filepath;
        private final String domain;
        private final ContentType type;
        private final List<String> lines;
        private final boolean reload;

        private PackFile(String filepath, String domain, ContentType type, List<String> lines, boolean reload) {
            this.filepath = filepath;
            this.domain = domain;
            this.type = type;
            this.lines = lines;
            this.reload = reload;
        }

        public ContentType getType() {
            return this.type;
        }

        /**
         * Reads the file in the format its extension names, and has its type's registry build the run-time value.
         * Errors are logged with the file name, line and key, and the file is then skipped by returning null.
         */
        @Nullable
        public LoadedContent<?, ?> parse() {
            try (ReportCollector.FileScope scope = ReportCollector.enterFile(this.domain, this.filepath)) {
                LoadedContent<?, ?> content = read();
                if (content != null) {
                    scope.loaded();
                }
                return content;
            }
        }

        @Nullable
        private LoadedContent<?, ?> read() {
            String name = Files.getNameWithoutExtension(this.filepath);
            if (!ResourceLocation.isValidPath(name)) {
                // the name becomes an id; an invalid one used to stop the whole load
                ModLogger.get().error("Skipping '{}' in addon '{}': a content file name may only use lower-case "
                        + "letters, digits, '_', '-' and '.'", this.filepath, this.domain);
                return null;
            }
            AddonResourceLocation location = new AddonResourceLocation(Ids.of(name), this.domain);
            if (!this.reload) {
                ModLogger.get().debug("content file({}) loading start.", location);
            }

            try {
                String extension = Files.getFileExtension(this.filepath).toLowerCase(Locale.ROOT);
                ContentFormat<?> format = ContentFormat.of(this.type);
                if (!format.reads(extension)) {
                    ModLogger.get().warn("Skipping '{}' in addon '{}': no reader for '.{}' files",
                            this.filepath, this.domain, extension);
                    return null;
                }
                ContentEntry<?> entry = format.read(this.type, location, this.filepath, extension, this.lines,
                        this.reload);
                return ContentRegistries.registry(this.type).bind(entry);
            } catch (ContentParseException ex) {
                ModLogger.get().error("Failed to load '{}' from addon '{}'{}: {}", this.filepath, this.domain,
                        describeLocation(ex), ex.getMessage());
                ModLogger.get().debug("Parse error details for '{}'", this.filepath, ex);
                return null;
            } catch (Exception ex) {
                ModLogger.get().error("Failed to load '{}' from addon '{}': {}", this.filepath, this.domain,
                        ContentParseException.describe(ex), ex);
                return null;
            }
        }

        private static String describeLocation(ContentParseException ex) {
            StringBuilder where = new StringBuilder();
            if (ex.getLineNo() != ContentParseException.UNKNOWN_LINE) {
                where.append(" at line ").append(ex.getLineNo());
            }
            if (ex.getKeyPath() != null) {
                where.append(where.isEmpty() ? " at" : ",").append(" key '").append(ex.getKeyPath()).append('\'');
            }
            return where.toString();
        }
    }
}
