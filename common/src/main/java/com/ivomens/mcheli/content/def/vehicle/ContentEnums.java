package com.ivomens.mcheli.content.def.vehicle;

import java.util.Locale;

/** Parsing shared by the enums that content files can name. */
final class ContentEnums {

    private ContentEnums() {}

    /**
     * Parses a content value: the constant name (case insensitive, '-' or ' ' accepted for '_') or its ordinal
     * number. Returns {@code fallback} for anything else.
     */
    static <E extends Enum<E>> E parse(Class<E> type, Object value, E fallback) {
        E[] constants = type.getEnumConstants();
        if (value instanceof Number number) {
            return byOrdinal(constants, number.intValue(), fallback);
        }
        if (value == null) {
            return fallback;
        }
        String name = value.toString().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (name.isEmpty()) {
            return fallback;
        }
        try {
            return byOrdinal(constants, Integer.parseInt(name), fallback);
        } catch (NumberFormatException ignored) {
        }
        for (E constant : constants) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return fallback;
    }

    private static <E> E byOrdinal(E[] constants, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < constants.length ? constants[ordinal] : fallback;
    }
}
