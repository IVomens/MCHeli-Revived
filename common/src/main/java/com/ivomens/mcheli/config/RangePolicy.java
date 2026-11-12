package com.ivomens.mcheli.config;

/** What a number option does with a value outside its range. */
public enum RangePolicy {
    /** Use the nearest bound. */
    CLAMP("using "),
    /** Use the default. */
    USE_DEFAULT("using the default, ");

    private final String using;

    RangePolicy(String using) {
        this.using = using;
    }

    /** The end of a problem report: which value is used instead. */
    String using(String value) {
        return this.using + value;
    }
}
