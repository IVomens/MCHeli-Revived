package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.util.ModLogger;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports content keys that older packs still use but the game no longer reads. The parsers recognise these
 * keys and skip them. Each key is reported once per content load so a big pack doesn't flood the log.
 * docs/CONTENT_FORMAT_NOTES.md lists them for pack authors.
 */
public final class UnsupportedSettings {

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private UnsupportedSettings() {}

    /** Called at the start of a content load so every key is reported again once. */
    public static void resetReports() {
        REPORTED.clear();
    }

    /** Logs that {@code key} was skipped, unless it was already reported during this load. */
    public static void report(String key, @Nullable String file) {
        if (REPORTED.add(key)) {
            ModLogger.get().warn("Setting '{}' is no longer supported and is ignored (first seen in '{}')", key,
                    file != null ? file : "?");
        }
    }
}
