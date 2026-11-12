package com.ivomens.mcheli.core.content.schema;

import java.util.function.Function;

/**
 * The format of one entry of a YAML list, such as a seat in {@code Seats} or a hatch in
 * {@code Components.Hatch}: its keys, declared like a section, and how they become the entry record. The
 * readers and the emitter walk it like the file's own schema, so an entry gets the same unknown-key reports,
 * clamping, key paths ({@code Seats[2].Camera.Pos}) and line numbers as the rest of the file.
 *
 * @param what  how messages name one entry, such as {@code "seat"}
 * @param root  the entry's keys, in the order the emitter writes them
 * @param build turns the values read for one entry into its record; {@link Values#index()} is its place
 * @param <E>   the entry record
 */
public record EntrySchema<E>(String what, Section<E, E> root, Function<Values, E> build) {

    @SafeVarargs
    public static <E> EntrySchema<E> of(String what, Function<Values, E> build, Member<E>... members) {
        return new EntrySchema<>(what, Section.root(members), build);
    }

    /** The same entry with keys older packs still write but the game no longer reads. */
    public EntrySchema<E> unsupported(String... keys) {
        return new EntrySchema<>(this.what, this.root.unsupported(keys), this.build);
    }

    /** The same entry with a key it cannot do without; see {@link Section#requires}. */
    public EntrySchema<E> requires(Field<E, ?> field) {
        return new EntrySchema<>(this.what, this.root.requires(field), this.build);
    }

    /**
     * The same entry, which may also be written as a plain value that sets {@code field}:
     * {@code Call: common_pilot} for {@code Call: {Hud: common_pilot}}. See {@link Section#shorthand}.
     */
    public EntrySchema<E> shorthand(Field<E, ?> field) {
        return new EntrySchema<>(this.what, this.root.shorthand(field), this.build);
    }

    /** The entry the given values describe. */
    public E build(Values values) {
        return this.build.apply(values);
    }
}
