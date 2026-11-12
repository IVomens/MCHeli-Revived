package com.ivomens.mcheli.content.def.vehicle;

public enum RadarType {
    MODERN_AA,
    EARLY_AA,
    MODERN_AS,
    EARLY_AS;

    /** Parses a name or ordinal from a content file; see {@link ContentEnums#parse}. */
    public static RadarType parse(Object value, RadarType fallback) {
        return ContentEnums.parse(RadarType.class, value, fallback);
    }
}
