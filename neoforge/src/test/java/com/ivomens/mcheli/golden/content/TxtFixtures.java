package com.ivomens.mcheli.golden.content;


import com.ivomens.mcheli.content.registry.LoadedContent;

import java.nio.file.Path;
import java.util.Map;

/**
 * The legacy TXT corpus in {@code src/test/resources/content/txt}. No TXT content is bundled any
 * more, but old packs still load through the TXT readers, so these files exercise every key they
 * know, including the cases where the order of lines matters. They load as an addon on top of the
 * bundled content, so vehicles can mount bundled weapons and call bundled HUDs.
 */
public final class TxtFixtures {

    private TxtFixtures() {
    }

    public static Path pack() {
        return ContentPipeline.testPack("txt");
    }

    /** Every fixture file by its path inside the pack. */
    public static Map<String, LoadedContent<?, ?>> load() {
        return ContentPipeline.load(ContentPipeline.bundledPack(), pack());
    }
}
