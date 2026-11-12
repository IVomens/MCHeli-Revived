package com.ivomens.mcheli.config;

import java.util.List;
import java.util.function.Consumer;

/** {@code true} or {@code false}, in any case. Any other word keeps the value read so far. */
public record BoolOption(String key, Boolean defaultValue, List<String> comment, String validSince)
        implements Option<Boolean> {

    public BoolOption {
        comment = List.copyOf(comment);
    }

    public static BoolOption of(String key, boolean defaultValue) {
        return new BoolOption(key, defaultValue, List.of(), "");
    }

    public BoolOption describe(String... lines) {
        return new BoolOption(this.key, this.defaultValue, List.of(lines), this.validSince);
    }

    @Override
    public Boolean read(Boolean current, String key, String raw, Consumer<String> problems) {
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        problems.accept(key + " = " + raw + " is not true or false; keeping " + current);
        return current;
    }

    @Override
    public List<String> lines(Boolean value) {
        return List.of(this.key + " = " + value);
    }
}
