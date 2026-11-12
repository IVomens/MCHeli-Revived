package com.ivomens.mcheli.core.content.schema;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One map inside a structured YAML value, such as an entry of {@code Ammo.AmmoItems}. Keys are read with
 * their alternative spellings, and {@link #reportUnusedKeys()} names the keys nothing asked for.
 */
public final class YamlEntry {

    private final Map<String, Object> map;
    private final ReadContext context;
    private final String what;
    private final Set<String> used = new HashSet<>();

    private YamlEntry(Map<String, Object> map, ReadContext context, String what) {
        this.map = map;
        this.context = context;
        this.what = what;
    }

    /** @param what how messages name the entry, such as {@code "ammo item 2"} */
    public static YamlEntry of(Object raw, ReadContext context, String what) {
        return new YamlEntry(Codecs.map(raw), context, what);
    }

    /** The value of the first key present, or {@code fallback} when none is. */
    public <T> T read(ValueCodec<T> codec, T fallback, String... keys) {
        for (String key : keys) {
            if (this.map.containsKey(key)) {
                this.used.add(key);
                return codec.fromYaml(this.map.get(key), fallback, message -> this.context.warn(
                        this.what + " " + key + ": " + message));
            }
        }
        return fallback;
    }

    /** The value of the first key present; the entry cannot do without it. */
    public <T> T require(ValueCodec<T> codec, String... keys) {
        for (String key : keys) {
            if (this.map.containsKey(key) && this.map.get(key) != null) {
                return read(codec, null, key);
            }
        }
        throw new ContentValueException(this.what + " needs a '" + keys[0] + "' key");
    }

    /** The raw value of the first key present, or null. */
    public Object raw(String... keys) {
        for (String key : keys) {
            if (this.map.containsKey(key)) {
                this.used.add(key);
                return this.map.get(key);
            }
        }
        return null;
    }

    public void reportUnusedKeys() {
        List<String> unused = this.map.keySet().stream().filter(key -> !this.used.contains(key)).sorted().toList();
        for (String key : unused) {
            this.context.warn("unknown key '" + key + "' in " + this.what + ", ignored");
        }
    }
}
