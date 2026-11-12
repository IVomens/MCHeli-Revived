package com.ivomens.mcheli.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A key that may appear on many lines, each adding one entry. One list can collect several keys
 * (the eleven {@code DamageVs*} keys share one list), so each entry remembers which key it came
 * with and the file is written back in the order it was read. When a file has no entry at all, the
 * default entries are used.
 */
public record ListOption(List<String> keys, List<ListOption.Entry> defaultValue)
        implements Option<List<ListOption.Entry>> {

    /** One line of the list: the key it was written with, in its canonical spelling, and its text. */
    public record Entry(String key, String value) {
    }

    public ListOption {
        keys = List.copyOf(keys);
        defaultValue = List.copyOf(defaultValue);
    }

    public static ListOption of(String key, List<String> defaultValues) {
        return new ListOption(List.of(key), defaultValues.stream().map(value -> new Entry(key, value)).toList());
    }

    /** The texts of the entries, for lists with a single key. */
    public static List<String> values(List<Entry> entries) {
        return entries.stream().map(Entry::value).toList();
    }

    @Override
    public String key() {
        return this.keys.getFirst();
    }

    @Override
    public List<String> comment() {
        return List.of();
    }

    @Override
    public String validSince() {
        return "";
    }

    @Override
    public boolean matches(String key) {
        return this.keys.stream().anyMatch(key::equalsIgnoreCase);
    }

    @Override
    public List<Entry> unreadValue() {
        return List.of();
    }

    @Override
    public List<Entry> read(List<Entry> current, String key, String raw, Consumer<String> problems) {
        String canonical = this.keys.stream().filter(key::equalsIgnoreCase).findFirst().orElse(key);
        List<Entry> entries = new ArrayList<>(current);
        entries.add(new Entry(canonical, raw));
        return List.copyOf(entries);
    }

    @Override
    public List<String> lines(List<Entry> value) {
        return value.stream().map(entry -> entry.key() + " = " + entry.value()).toList();
    }
}
