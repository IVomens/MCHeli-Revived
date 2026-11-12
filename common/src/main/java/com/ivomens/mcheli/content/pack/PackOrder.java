package com.ivomens.mcheli.content.pack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The order addon packs load in, which decides which file wins when two packs define content of the same type and
 * name: the pack loaded later replaces the earlier pack's file, whole.
 * <ol>
 *     <li>The bundled content ({@code mcheli_addons/default}) loads first, so every addon can replace it.</li>
 *     <li>The packs other mods ship ({@link ModContentPacks}) follow, by mod id.</li>
 *     <li>The other packs in {@code mcheli_addons/} follow by file name, ignoring case ({@code a_pack} before
 *     {@code B_pack} before {@code zz_overrides}); names that differ only in case keep a fixed order. So a
 *     player's pack can replace a mod's.</li>
 * </ol>
 * An author who wants a pack to win over another names it to sort later, for example with a {@code zz_} prefix.
 * docs/ADDON_AUTHORING.md describes the rules for pack authors.
 */
public final class PackOrder {

    /** Packs in load order. */
    public static final Comparator<File> LOAD_ORDER = Comparator.comparing(PackOrder::isBundled).reversed()
            .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(File::getName);

    private PackOrder() {
    }

    /**
     * The packs in load order: the bundled content and the player's packs from {@code mcheli_addons/}, already
     * sorted by {@link #LOAD_ORDER}, with the mods' packs (in their own order) between the two.
     */
    public static List<File> withModPacks(File[] addonFolderPacks, List<File> modPacks) {
        List<File> ordered = new ArrayList<>();
        Arrays.stream(addonFolderPacks).filter(PackOrder::isBundled).forEach(ordered::add);
        ordered.addAll(modPacks);
        Arrays.stream(addonFolderPacks).filter(pack -> !isBundled(pack)).forEach(ordered::add);
        return ordered;
    }

    /** Whether the pack file is the extracted bundled content. */
    public static boolean isBundled(File packFile) {
        return BuiltinAddonPack.EXTRACTED_FOLDER_NAME.equals(packFile.getName());
    }
}
