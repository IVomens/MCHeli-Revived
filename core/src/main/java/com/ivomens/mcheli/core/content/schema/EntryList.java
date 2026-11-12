package com.ivomens.mcheli.core.content.schema;

import java.util.List;
import java.util.function.Supplier;

/**
 * The value of a YAML map, or a list of maps, whose keys have their own {@link EntrySchema}: a seat's
 * {@code Camera}, the {@code Seats} list. Declared with {@link Field#entry} and {@link Field#listOf}. The
 * YAML reader and the emitter recognise it and walk the entry schema, which gives every entry key paths and
 * line numbers; legacy TXT files spell the value through the field's own TXT codec or add list entries one
 * line at a time through {@link TxtTuple}s. It is therefore never asked to spell a value itself.
 * <p>
 * The entry schema is looked up lazily, so an entry may hold a list of its own kind (a vehicle part with
 * child parts).
 *
 * @param <T> the value: one entry record, or a list of them
 * @param <E> the entry record
 */
public abstract sealed class EntryList<T, E> implements ValueCodec<T> {

    private final Supplier<EntrySchema<E>> schema;

    private EntryList(Supplier<EntrySchema<E>> schema) {
        this.schema = schema;
    }

    public EntrySchema<E> schema() {
        return this.schema.get();
    }

    @Override
    public T fromYaml(Object raw, T previous, ReadContext context) {
        throw new UnsupportedOperationException("entries are read key by key by the YAML reader");
    }

    @Override
    public T fromText(String text, T previous, ReadContext context) {
        throw new UnsupportedOperationException("TXT spells entries through the field's TXT codec or a TxtTuple");
    }

    @Override
    public Object toYaml(T value) {
        throw new UnsupportedOperationException("entries are written key by key by the emitter");
    }

    /** A list of entries, such as {@code Seats}. */
    public static final class Many<E> extends EntryList<List<E>, E> {

        Many(Supplier<EntrySchema<E>> schema) {
            super(schema);
        }
    }

    /** A single map read through an entry schema, such as a seat's {@code Camera}. */
    public static final class One<E> extends EntryList<E, E> {

        One(Supplier<EntrySchema<E>> schema) {
            super(schema);
        }
    }
}
