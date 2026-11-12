package com.ivomens.mcheli.content.def.weapon;

import java.util.List;
import java.util.Locale;

/**
 * The kind of Hammer's Nuclear Tech explosion, YAML {@code NTM.PayloadType}, with the spellings files use.
 */
public enum NtmPayloadType {
    NONE,
    NTM_VNT("NTM_VNT", "VNT"),
    NTM_NT("NTM_NT", "NT"),
    NTM_MINI_NUKE("NTM_MININUKE", "MININUKE"),
    NTM_NUKE("NTM_NUKE", "NUKE"),
    NTM_CHEMICAL("CHEMICAL", "NTM_CHEMICAL"),
    NTM_MIST("NTM_MIST", "MIST"),
    NTM_FIRE("NTM_FIRE", "FIRE", "PHOSPHORUS", "PHOSPHOR", "WP", "BALEFIRE");

    private final List<String> spellings;

    NtmPayloadType(String... spellings) {
        this.spellings = List.of(spellings);
    }

    /** Reads a written name in any case; an unknown name means no payload. */
    static NtmPayloadType parse(String text) {
        String name = text.trim().toUpperCase(Locale.ROOT);
        for (NtmPayloadType type : values()) {
            if (type.spellings.contains(name)) {
                return type;
            }
        }
        return NONE;
    }
}
