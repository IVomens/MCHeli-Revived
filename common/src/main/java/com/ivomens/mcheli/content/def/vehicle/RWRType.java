package com.ivomens.mcheli.content.def.vehicle;

public enum RWRType {
    FOUR_WAY,
    ALL_WAY_EARLY,
    DIGITAL;

    /** Parses a name or ordinal from a content file; see {@link ContentEnums#parse}. */
    public static RWRType parse(Object value, RWRType fallback) {
        return ContentEnums.parse(RWRType.class, value, fallback);
    }
}
