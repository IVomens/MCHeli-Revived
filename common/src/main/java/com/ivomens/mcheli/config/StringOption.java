package com.ivomens.mcheli.config;

import java.util.List;
import java.util.function.Consumer;

/** Free text, kept as written. Options that need structure parse it when their section is built. */
public record StringOption(String key, String defaultValue, List<String> comment, String validSince)
        implements Option<String> {

    public StringOption {
        comment = List.copyOf(comment);
    }

    public static StringOption of(String key, String defaultValue) {
        return new StringOption(key, defaultValue, List.of(), "");
    }

    public StringOption describe(String... lines) {
        return new StringOption(this.key, this.defaultValue, List.of(lines), this.validSince);
    }

    public StringOption since(String version) {
        return new StringOption(this.key, this.defaultValue, this.comment, version);
    }

    @Override
    public String read(String current, String key, String raw, Consumer<String> problems) {
        return raw;
    }

    @Override
    public List<String> lines(String value) {
        return List.of(this.key + " = " + value);
    }
}
