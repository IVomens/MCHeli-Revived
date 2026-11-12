package com.ivomens.mcheli.core.content.schema;

import com.ivomens.mcheli.core.content.schema.TxtKey.TxtRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Everything the game knows about one content type's file format: the YAML sections and settings in the
 * order the emitter writes them, the legacy TXT keys, and how the values become a definition. The YAML
 * and TXT readers and the emitter are all driven by it, so a setting is declared once.
 *
 * @param <D> the definition the files are read into
 */
public final class Schema<D extends ContentDefinition> {

    /**
     * The content schema version this game reads. A YAML settings file may say which version it is written for
     * with a top-level {@link #VERSION_KEY}; a file without one is version 1. Once the format changes in a way
     * older files cannot follow, the version goes up and the reader migrates older files before binding them.
     */
    public static final int VERSION = 1;
    /** The top-level YAML key that names the version a file is written for. */
    public static final String VERSION_KEY = "SchemaVersion";

    private final Section<D, D> root;
    private final Function<Values, D> build;
    private final Map<String, TxtKey> txtKeys;
    private final List<Field<?, ?>> fields;
    /** TXT keys an in-game reload reads again: every key but these when {@link #reloads}, none otherwise. */
    private final boolean reloads;
    private final Set<String> fixedOnReload;
    private final List<Consumer<Values>> afterTxt;

    private Schema(Builder<D> builder) {
        this.root = builder.root;
        this.build = builder.build;
        this.txtKeys = Map.copyOf(builder.txtKeys);
        this.fields = List.copyOf(builder.fields);
        this.reloads = builder.reloads;
        this.fixedOnReload = Set.copyOf(builder.fixedOnReload);
        this.afterTxt = List.copyOf(builder.afterTxt);
    }

    /**
     * @param root  the top of the YAML file
     * @param build turns the values read from a file into the definition
     */
    public static <D extends ContentDefinition> Builder<D> builder(Section<D, D> root, Function<Values, D> build) {
        return new Builder<>(root, build);
    }

    public Section<D, D> root() {
        return this.root;
    }

    /** The definition the values describe. */
    public D build(Values values) {
        return this.build.apply(values);
    }

    /** The definition of a file that sets nothing. */
    public D defaults(String name) {
        return build(new Values(name));
    }

    /** What a lower-case TXT key does, if this format knows it. */
    public Optional<TxtKey> txtKey(String key) {
        return Optional.ofNullable(this.txtKeys.get(key));
    }

    /** Every TXT key of the format, lower case. */
    public Set<String> txtKeys() {
        return this.txtKeys.keySet();
    }

    /** Every setting, in declaration order. */
    public List<Field<?, ?>> fields() {
        return this.fields;
    }

    /**
     * Applies the legacy rules that need the whole TXT file, once its last line is read: values that lines
     * gave in any order and that only together say where they belong (a vehicle's {@code HUD} line names the
     * HUD of seats that may come later).
     */
    public void finishTxt(Values values) {
        this.afterTxt.forEach(rule -> rule.accept(values));
    }

    /**
     * Whether an in-game reload of a TXT file reads the lower-case {@code key} again. A key it skips takes its
     * default, as it always has.
     */
    public boolean readsOnReload(String key) {
        return this.reloads && !this.fixedOnReload.contains(key);
    }

    public static final class Builder<D extends ContentDefinition> {

        private final Section<D, D> root;
        private final Function<Values, D> build;
        private final Map<String, TxtKey> txtKeys = new HashMap<>();
        private final List<Field<?, ?>> fields = new ArrayList<>();
        private boolean reloads;
        private final Set<String> fixedOnReload = new HashSet<>();
        private final List<Consumer<Values>> afterTxt = new ArrayList<>();

        private Builder(Section<D, D> root, Function<Values, D> build) {
            this.root = root;
            this.build = build;
            collectFields(root);
            for (Field<?, ?> field : this.fields) {
                field.txtKeys().forEach(key -> addTxtKey(key, new TxtKey.SetsField(field)));
            }
        }

        /** A TXT key with legacy behaviour that no single field describes. */
        public Builder<D> txtRule(String key, TxtRule rule) {
            addTxtKey(key, new TxtKey.AppliesRule(rule));
            return this;
        }

        /** TXT keys older packs still write but the game no longer reads. */
        public Builder<D> txtUnsupported(String... keys) {
            for (String key : keys) {
                addTxtKey(key, new TxtKey.Unsupported());
            }
            return this;
        }

        /**
         * An in-game reload of a TXT file reads every line again except those with these keys. Without this
         * call a reload reads none of the lines.
         */
        public Builder<D> reloadableExcept(String... keys) {
            this.reloads = true;
            for (String key : keys) {
                this.fixedOnReload.add(key.toLowerCase(Locale.ROOT));
            }
            return this;
        }

        /** A legacy rule run once a TXT file's last line is read; see {@link Schema#finishTxt}. */
        public Builder<D> afterTxt(Consumer<Values> rule) {
            this.afterTxt.add(rule);
            return this;
        }

        public Schema<D> build() {
            return new Schema<>(this);
        }

        private <S> void collectFields(Section<?, S> section) {
            for (Member<S> member : section.members()) {
                switch (member) {
                    case Field<S, ?> field -> this.fields.add(field);
                    case Section<S, ?> child -> collectFields(child);
                    case KeyAlias<S> alias -> {
                        // the target is collected where it is declared
                    }
                }
            }
        }

        private void addTxtKey(String key, TxtKey meaning) {
            String lowerCase = key.toLowerCase(Locale.ROOT);
            if (this.txtKeys.putIfAbsent(lowerCase, meaning) != null) {
                throw new IllegalStateException("TXT key '" + lowerCase + "' is declared twice");
            }
        }
    }
}
