package com.ivomens.mcheli.core.content.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A display name with optional translations by language code. In YAML it is either the name as text, or a
 * map of language codes to names where {@code DEFAULT} is the name itself:
 * <pre>
 * DisplayName:
 *   ja_JP: M18 発煙手榴弾(緑)
 *   DEFAULT: M18 smoke (Green)
 * </pre>
 *
 * @param translations names by language code, in file order
 */
public record LocalizedName(String name, Map<String, String> translations) {

    private static final String DEFAULT_KEY = "DEFAULT";

    /** YAML text or language map; a TXT {@code DisplayName} line sets only the name. */
    public static final ValueCodec<LocalizedName> CODEC = new ValueCodec<>() {
        @Override
        public LocalizedName fromYaml(Object raw, LocalizedName previous, ReadContext context) {
            if (raw instanceof String text) {
                return previous.withName(text.trim());
            }
            if (raw instanceof Map<?, ?> names) {
                LocalizedName result = previous;
                for (Map.Entry<?, ?> entry : names.entrySet()) {
                    String language = String.valueOf(entry.getKey());
                    String name = String.valueOf(entry.getValue());
                    result = language.equals(DEFAULT_KEY) ? result.withName(name) : result.with(language, name);
                }
                return result;
            }
            throw new ContentValueException("expected the name as text or a map of language to name but got "
                    + Codecs.describe(raw));
        }

        @Override
        public LocalizedName fromText(String text, LocalizedName previous, ReadContext context) {
            return previous.withName(text);
        }

        @Override
        public ValueShape shape() {
            return ValueShape.either(ValueShape.text(), ValueShape.mapOf(ValueShape.anything()))
                    .note("The name, or a map of language codes (en_us, ja_jp) to names where DEFAULT is the name "
                            + "itself.");
        }

        @Override
        public Object toYaml(LocalizedName value) {
            if (value.translations.isEmpty()) {
                return value.name;
            }
            Map<String, String> names = new LinkedHashMap<>(value.translations);
            names.put(DEFAULT_KEY, value.name);
            return names;
        }
    };

    public LocalizedName {
        translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }

    public static LocalizedName of(String name) {
        return new LocalizedName(name, Map.of());
    }

    public LocalizedName withName(String newName) {
        return new LocalizedName(newName, this.translations);
    }

    /** Adds or replaces the name in one language. */
    public LocalizedName with(String language, String translatedName) {
        Map<String, String> names = new LinkedHashMap<>(this.translations);
        names.put(language, translatedName);
        return new LocalizedName(this.name, names);
    }
}
