package com.ivomens.mcheli.core.content.schema;

import java.util.List;

/**
 * A second YAML key, in another section, for a setting declared elsewhere: {@code Sound.Name} sets the
 * same fire sound as {@code Sound.Locations.Fire}. The reader accepts it; the emitter only writes the
 * setting's own key.
 *
 * @param key      the other key
 * @param target   the setting it sets
 * @param spelling how the value is read under this key; usually the setting's own codec
 * @param <S>      the record of the section the key sits in
 */
public record KeyAlias<S>(String key, Field<?, ?> target, ValueCodec<?> spelling) implements Member<S> {

    public KeyAlias(String key, Field<?, ?> target) {
        this(key, target, target.codec());
    }

    /**
     * An older key that reads the setting its own way, such as a top-level {@code RadarType} that falls back
     * to another type than {@code AircraftFeatures.Radar.Type} for a name it does not know.
     */
    public static <S, T> KeyAlias<S> spelled(String key, Field<?, T> target, ValueCodec<T> spelling) {
        return new KeyAlias<>(key, target, spelling);
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }
}
