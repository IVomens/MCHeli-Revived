package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hammer's Nuclear Tech explosion settings, the YAML {@code NTM} section, used only when that mod is
 * installed. {@link NtmPayloadReader} documents the keys.
 * <p>
 * The subsections are read into the records of {@link NtmSections} when the file is read. Two payloads are
 * equal when their sections are, and the emitter writes the section back as it was.
 *
 * @param section  the section as written, for the emitter
 * @param vnt      the {@code VNT} subsection, or null
 * @param nt       the {@code NT} subsection, or null
 * @param miniNuke the {@code MiniNuke} subsection or preset, or null
 * @param mist     the {@code Mist} subsection, or null
 * @param fire     the {@code Fire} subsection, or null
 */
public record NtmPayload(Map<String, Object> section, NtmPayloadType type, boolean effectOnly,
                         @Nullable NtmSections.Vnt vnt, @Nullable NtmSections.Nt nt,
                         @Nullable NtmSections.MiniNuke miniNuke, @Nullable NtmSections.Mist mist,
                         @Nullable NtmSections.Fire fire) {

    public static final ValueCodec<NtmPayload> CODEC = new ValueCodec<>() {
        @Override
        public NtmPayload fromYaml(Object raw, NtmPayload previous, ReadContext context) {
            return NtmPayloadReader.read(Codecs.map(raw), context);
        }

        @Override
        public NtmPayload fromText(String text, NtmPayload previous, ReadContext context) {
            throw new UnsupportedOperationException("NTM payloads only exist in YAML");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.mapOf(ValueShape.anything())
                    .note("Hammer's Nuclear Tech explosion settings, used only with that mod installed.");
        }

        @Override
        public Object toYaml(NtmPayload payload) {
            return payload.section;
        }
    };

    public NtmPayload {
        section = frozenCopy(section);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NtmPayload payload && this.section.equals(payload.section);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.section);
    }

    @SuppressWarnings("unchecked")
    private static <T> T frozenCopy(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(key, frozenCopy(entry)));
            return (T) Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(entry -> copy.add(frozenCopy(entry)));
            return (T) Collections.unmodifiableList(copy);
        }
        return value;
    }
}
