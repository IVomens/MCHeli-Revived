package com.ivomens.mcheli.content.registry;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime translations for names that come from content files (aircraft, weapons, throwables,
 * custom items: their display names live in the YAML/TXT definitions, not in a lang file).
 * <p>
 * The client's language lookup consults these for keys the loaded lang files do not define (see
 * {@code mixin.MixinClientLanguage}), so a resource pack can still override every name.
 */
public final class ContentTranslations {

    public static final String DEFAULT_LANG = "en_us";

    /** lang code -> translation key -> text */
    private static final Map<String, Map<String, String>> TRANSLATIONS = new ConcurrentHashMap<>();

    private ContentTranslations() {}

    /** Adds a translation for a raw translation key. Null arguments are ignored. */
    public static void put(String lang, String translationKey, String text) {
        if (lang == null || translationKey == null || text == null) {
            return;
        }
        TRANSLATIONS.computeIfAbsent(lang.toLowerCase(Locale.ROOT), l -> new ConcurrentHashMap<>()).put(translationKey, text);
    }

    /** The runtime translation for the key in the given language, falling back to English; null if none. */
    @Nullable
    public static String lookup(@Nullable String lang, String translationKey) {
        Map<String, String> forLang = lang != null ? TRANSLATIONS.get(lang) : null;
        String text = forLang != null ? forLang.get(translationKey) : null;
        if (text == null && !DEFAULT_LANG.equals(lang)) {
            Map<String, String> english = TRANSLATIONS.get(DEFAULT_LANG);
            text = english != null ? english.get(translationKey) : null;
        }
        return text;
    }

    public static boolean isEmpty() {
        return TRANSLATIONS.isEmpty();
    }
}
