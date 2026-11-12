package com.ivomens.mcheli.content.registry;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.ivomens.mcheli.content.bind.UnsupportedSettings;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.pack.AddonPack;
import com.ivomens.mcheli.content.report.ContentLog;
import com.ivomens.mcheli.content.report.ContentReport;
import com.ivomens.mcheli.content.report.ReportCollector;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.content.source.ContentLoader;
import com.ivomens.mcheli.content.source.FileContentLoader;
import com.ivomens.mcheli.content.source.FolderContentLoader;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Entry point of the content pipeline. Each feature owns the {@link ContentRegistry} of its content types and
 * hands it to {@link #register} at start-up; this class fills them.
 *
 * <p>Loading happens once during mod setup, in {@link #loadContents(File)}:
 * <ol>
 *     <li><b>Discover</b>: {@link AddonManager} lists the addon packs in {@code mcheli_addons/} (folders and
 *     zip/jar files) and the packs other mods register through the API
 *     ({@link com.ivomens.mcheli.content.pack.ModContentPacks}), and reads their {@code pack.mcmeta}.</li>
 *     <li><b>Read</b>: for each pack a {@link ContentLoader} collects the files under
 *     {@code assets/mcheli/<type folder>/}.</li>
 *     <li><b>Parse</b>: each file is read by its type's {@link ContentFormat}, with the reader for its file
 *     extension (YAML or the legacy TXT format), into an immutable definition, and the type's registry builds
 *     the run-time value from it. A file that fails is logged with its name, line and key and skipped.</li>
 *     <li><b>Register</b>: the files go into their registry keyed by file name (without extension). When two
 *     packs define the same name, the pack loaded later wins, whole ({@link com.ivomens.mcheli.content.pack.PackOrder}
 *     says which pack that is); a replacement is reported, as a warning unless it replaces bundled content.</li>
 *     <li><b>Publish</b>: every registry tells its listeners it changed, again after every reload.</li>
 *     <li><b>Report</b>: every problem logged on the way is collected ({@link ReportCollector}) and written to
 *     {@code logs/mcheli-content.log}; the main log gets one summary line.</li>
 * </ol>
 * HUDs and weapons are parsed first because vehicles refer to them by name.
 */
public final class ContentRegistries {

    /** The order types are read in: what a type links to is read before it. */
    private static final List<ContentType> LOAD_ORDER = List.of(ContentType.HUD, ContentType.WEAPON,
            ContentType.HELICOPTER, ContentType.PLANE, ContentType.SHIP, ContentType.TANK, ContentType.VEHICLE,
            ContentType.THROWABLE);
    private static final Pattern PATH_SPLIT = Pattern.compile("[/\\\\]+");
    /** Minimum path depth of a content file: assets/mcheli/&lt;type&gt;/&lt;file&gt;. */
    private static final int CONTENT_PATH_DEPTH = 4;

    private static final Map<ContentType, ContentRegistry<?, ?>> REGISTRIES = new EnumMap<>(ContentType.class);

    private ContentRegistries() {
    }

    /** Makes {@code registry} the one its type is loaded into. Call at start-up, before loading. */
    public static void register(ContentRegistry<?, ?> registry) {
        REGISTRIES.put(registry.type(), registry);
    }

    /** The registry of {@code type}; every type has one once the features registered theirs. */
    public static ContentRegistry<?, ?> registry(ContentType type) {
        ContentRegistry<?, ?> registry = REGISTRIES.get(type);
        if (registry == null) {
            throw new IllegalStateException("No registry for " + type.dirName + " content");
        }
        return registry;
    }

    /** Whether a file of {@code type} with this name is loaded. */
    public static boolean contains(ContentType type, String name) {
        ContentRegistry<?, ?> registry = REGISTRIES.get(type);
        return registry != null && registry.contains(name);
    }

    /**
     * Runs the whole content pipeline; see the class description. Everything it reports is also collected and
     * written to {@code logs/mcheli-content.log}, with a summary line in the main log.
     */
    public static void loadContents(File addonDir) {
        ContentLog.publish(load(() -> AddonManager.loadAddons(addonDir)), ContentLog.LOAD_REPORT);
    }

    /**
     * Loads the packs {@code discover} finds, in the order it lists them, into the registries.
     *
     * @return what the load found, problems from discovering the packs included
     */
    static ContentReport load(Supplier<List<AddonPack>> discover) {
        try (ReportCollector report = ReportCollector.open("Content load")) {
            List<AddonPack> addons = discover.get();
            ModLogger.log("Registered content packs: " + addons);
            UnsupportedSettings.resetReports();

            Multimap<ContentType, ContentLoader.PackFile> contents = read(addons, report);
            for (ContentType type : LOAD_ORDER) {
                ContentRegistry<?, ?> registry = registry(type);
                fill(registry, parse(registry, contents.get(type), report));
            }
            LOAD_ORDER.forEach(type -> registry(type).changed());
            return report.finish();
        }
    }

    /** The content types in the order they are read: what a type links to is read before it. */
    public static List<ContentType> loadOrder() {
        return LOAD_ORDER;
    }

    /** Lists the files of every pack, in pack order, and the packs in the report. Nothing is parsed yet. */
    static Multimap<ContentType, ContentLoader.PackFile> read(List<AddonPack> packs, ReportCollector report) {
        Multimap<ContentType, ContentLoader.PackFile> contents = LinkedHashMultimap.create();
        for (AddonPack pack : packs) {
            ModLogger.get().debug("addon({}) loading start.", pack.getDomain());
            report.pack(pack.getDomain(), pack.getFile() != null ? pack.getFile().getName() : "?");
            contents.putAll(getDefaultPackLoader(pack).load());
        }
        return contents;
    }

    /** Parses the files of one type in load order, and reports the files that replace others. */
    static <D extends ContentDefinition, T> List<LoadedContent<D, T>> parse(ContentRegistry<D, T> registry,
                                                                           Collection<ContentLoader.PackFile> files,
                                                                           ReportCollector report) {
        ModLogger.get().debug("content type({}) loading start. steps:{}", registry.type().dirName, files.size());
        List<LoadedContent<D, T>> parsed = files.stream().map(ContentLoader.PackFile::parse).filter(Objects::nonNull)
                .map(registry::cast).toList();
        Overrides.record(registry.type(), parsed, report);
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private static <D extends ContentDefinition, T> void fill(ContentRegistry<D, T> registry,
                                                              List<? extends LoadedContent<?, ?>> files) {
        registry.fill((List<LoadedContent<D, T>>) files);
    }

    /** Reads the file behind {@code old} again from its addon pack; returns {@code old} when that fails. */
    static <D extends ContentDefinition, T> LoadedContent<D, T> reread(ContentRegistry<D, T> registry,
                                                                       LoadedContent<D, T> old) {
        AddonPack addonPack = AddonManager.get(old.entry().location().getAddonDomain());
        if (addonPack == null) {
            return old;
        }
        return getDefaultPackLoader(addonPack).reread(registry, old);
    }

    /** Reads every file of {@code registry} again, each from its own pack. */
    static <D extends ContentDefinition, T> List<LoadedContent<D, T>> rereadAll(ContentRegistry<D, T> registry) {
        List<LoadedContent<D, T>> list = new ArrayList<>();
        String folder = registry.type().dirName;
        Predicate<String> onlyThisType = contentPathFilter(folder::equals);
        for (AddonPack addon : AddonManager.getLoadedAddons()) {
            // Only re-read entries from their own pack. Other packs do not have the file, and the reload keeps
            // the old value on a failed read, which would overwrite the freshly parsed one.
            List<LoadedContent<D, T>> ownContents = registry.loaded().stream()
                    .filter(content -> addon.getDomain().equals(content.entry().location().getAddonDomain()))
                    .toList();
            if (!ownContents.isEmpty()) {
                list.addAll(getPackLoader(addon, onlyThisType).reread(registry, ownContents));
            }
        }
        return list;
    }

    public static ContentLoader getDefaultPackLoader(AddonPack pack) {
        return getPackLoader(pack, contentPathFilter(ContentRegistries::canLoadContentDirName));
    }

    /** Content type folders to read; HUDs only matter to a client. */
    private static boolean canLoadContentDirName(String dir) {
        return Services.PLATFORM.isClient() && "hud".equals(dir) || ContentType.validateDirName(dir);
    }

    public static ContentLoader getPackLoader(AddonPack pack, Predicate<String> fileFilter) {
        String loaderVersion = pack.getLoaderVersion();
        return pack.getFile().isDirectory()
                ? new FolderContentLoader(pack.getDomain(), pack.getFile(), loaderVersion, fileFilter)
                : new FileContentLoader(pack.getDomain(), pack.getFile(), loaderVersion, fileFilter);
    }

    /**
     * Accepts paths of the form {@code .../assets/mcheli/<type folder>/...} whose type folder passes
     * {@code typeDirFilter}.
     */
    private static Predicate<String> contentPathFilter(Predicate<String> typeDirFilter) {
        return filepath -> {
            String[] parts = splitPath(filepath);
            int assets = indexOf(parts, "assets");
            if (assets < 0 || parts.length < assets + CONTENT_PATH_DEPTH) {
                return false;
            }
            return Ids.MOD_ID.equals(parts[assets + 1]) && typeDirFilter.test(parts[assets + 2]);
        };
    }

    private static String[] splitPath(String filepath) {
        String normalized = filepath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return PATH_SPLIT.split(normalized);
    }

    private static int indexOf(String[] parts, String needle) {
        for (int i = 0; i < parts.length; i++) {
            if (needle.equals(parts[i])) {
                return i;
            }
        }
        return -1;
    }
}
