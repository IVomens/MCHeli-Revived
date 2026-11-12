package com.ivomens.mcheli.core.content.schema;

import com.ivomens.mcheli.core.content.schema.TxtKey.TxtRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * A legacy TXT line that describes one list entry by position, such as
 * {@code AddSeat = x, y, z[, rotSeat[, unmountX, unmountY, unmountZ]]}. It declares which entry key each
 * position sets, so the line reads into the same entry record as the YAML list and documents itself:
 * <pre>
 * TxtTuple.of(SeatSchema.ENTRY).minimum(3)
 *         .vector(SeatSchema.POSITION)        // values 1 to 3
 *         .value(SeatSchema.ROTATING)         // value 4
 *         .vector(SeatSchema.UNMOUNT_POSITION) // values 5 to 7
 * </pre>
 * Values are read with the entry fields' TXT codecs. A trailing group the line leaves out keeps the entry's
 * default, or the legacy default given with it; a group is only read when all of its values are there, as
 * the legacy parser did. A line with fewer values than the minimum is ignored. Tuples are immutable.
 *
 * @param <E> the entry record
 */
public final class TxtTuple<E> {

    private final EntrySchema<E> entry;
    private final List<Slot<E, ?>> slots;
    private final List<Fixed<E, ?>> fixed;
    private final Predicate<String[]> accepts;
    private final Finish after;

    /**
     * One group of values.
     *
     * @param width       how many values the group takes: 1, 2 for a pair, 3 for a vector, or {@link #REST}
     * @param field       the entry key it sets, or null for a value the game never read
     * @param whenMissing what a line without the group gets, or null for the entry's default
     */
    private record Slot<E, T>(int width, Field<E, T> field, T whenMissing) {

        /** The width of a group that takes every value left on the line. */
        static final int REST = -1;

        /** How many values the group takes when {@code remaining} values are left on the line. */
        int widthOn(int remaining) {
            return this.width == REST ? Math.max(remaining, 0) : this.width;
        }
    }

    private record Fixed<E, T>(Field<E, T> field, T value) {

        void applyTo(Values values) {
            values.set(this.field, this.value);
        }
    }

    /** Legacy behaviour that no single position describes, run once the line's values are read. */
    @FunctionalInterface
    public interface Finish {

        /**
         * @param entry the values read for the new entry
         * @param file  the values of the whole file so far
         * @param count how many values the line has
         */
        void apply(Values entry, Values file, int count);
    }

    private TxtTuple(EntrySchema<E> entry, List<Slot<E, ?>> slots, List<Fixed<E, ?>> fixed,
                     Predicate<String[]> accepts,
                     Finish after) {
        this.entry = entry;
        this.slots = List.copyOf(slots);
        this.fixed = List.copyOf(fixed);
        this.accepts = accepts;
        this.after = after;
    }

    public static <E> TxtTuple<E> of(EntrySchema<E> entry) {
        return new TxtTuple<>(entry, List.of(), List.of(), values -> true, null);
    }

    /** The next value sets {@code field}. */
    public TxtTuple<E> value(Field<E, ?> field) {
        return with(new Slot<>(1, field, null));
    }

    /** The next value sets {@code field}; a line that ends before it gives {@code whenMissing}. */
    public <T> TxtTuple<E> value(Field<E, T> field, T whenMissing) {
        return with(new Slot<>(1, field, whenMissing));
    }

    /** The next two values are {@code field}, such as the {@code x, y} of a HUD position. */
    public TxtTuple<E> pair(Field<E, ?> field) {
        return with(new Slot<>(2, field, null));
    }

    /** The values left on the line are {@code field}, such as the points of a HUD line; none keeps the default. */
    public TxtTuple<E> rest(Field<E, ?> field) {
        return with(new Slot<>(Slot.REST, field, null));
    }

    /** The next three values are the {@code x, y, z} of {@code field}. */
    public TxtTuple<E> vector(Field<E, ?> field) {
        return with(new Slot<>(3, field, null));
    }

    /** The next three values are {@code field}; a line that ends before them gives {@code whenMissing}. */
    public <T> TxtTuple<E> vector(Field<E, T> field, T whenMissing) {
        return with(new Slot<>(3, field, whenMissing));
    }

    /** The next value is one the game never read, such as the 9th value of {@code AddRack}. */
    public TxtTuple<E> ignored() {
        return with(new Slot<>(1, null, null));
    }

    /** A line with fewer values is ignored. */
    public TxtTuple<E> minimum(int count) {
        return accepting(values -> values.length >= count);
    }

    /** Only lines with exactly one of these numbers of values are read; others are ignored. */
    public TxtTuple<E> exactly(int... counts) {
        return accepting(values -> IntStream.of(counts).anyMatch(allowed -> allowed == values.length));
    }

    /**
     * Only lines whose values also pass {@code test} are read; others are ignored, such as a HUD
     * {@code DrawString} whose text is not in quotes.
     */
    public TxtTuple<E> accepting(Predicate<String[]> test) {
        return new TxtTuple<>(this.entry, this.slots, this.fixed, this.accepts.and(test), this.after);
    }

    /** Every entry this line adds has {@code value} for {@code field}, whatever the line says. */
    public <T> TxtTuple<E> fixed(Field<E, T> field, T value) {
        List<Fixed<E, ?>> changed = new ArrayList<>(this.fixed);
        changed.add(new Fixed<>(field, value));
        return new TxtTuple<>(this.entry, this.slots, changed, this.accepts, this.after);
    }

    /** Legacy behaviour applied after the values are read, such as a part name made from a count. */
    public TxtTuple<E> then(Finish rule) {
        return new TxtTuple<>(this.entry, this.slots, this.fixed, this.accepts, rule);
    }

    /** The line as a TXT rule that appends its entry to {@code list}. */
    public TxtRule appendingTo(Field<?, List<E>> list) {
        return (values, text, context) -> read(text, values, values.get(list).size(), context)
                .ifPresent(entry -> values.append(list, entry));
    }

    /**
     * The entry the line describes, or empty when the line has a number of values the format ignores.
     *
     * @param file  the values of the whole file so far
     * @param index the place the entry will take in its list
     */
    public Optional<E> read(String text, Values file, int index, ReadContext context) {
        return read(text, file.entry(index), context);
    }

    /**
     * The entry the line describes, read into {@code values}, which may already hold what the line itself
     * cannot say, such as its line number; empty when the format ignores the line.
     *
     * @param values the values of the new entry, made with {@link Values#entry} from the file's values
     */
    public Optional<E> read(String text, Values values, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (!this.accepts.test(parts)) {
            return Optional.empty();
        }
        Values file = values.parent().orElseThrow(() -> new IllegalArgumentException("not the values of an entry"));
        this.fixed.forEach(preset -> preset.applyTo(values));
        int position = 0;
        for (Slot<E, ?> slot : this.slots) {
            int width = slot.widthOn(parts.length - position);
            readSlot(slot, parts, position, width, values, context);
            position += width;
        }
        if (this.after != null) {
            this.after.apply(values, file, parts.length);
        }
        return Optional.of(this.entry.build(values));
    }

    private static <E, T> void readSlot(Slot<E, T> slot, String[] parts, int position, int width, Values values,
                                        ReadContext context) {
        if (slot.field == null || width == 0) {
            return;
        }
        if (position + width > parts.length) {
            if (slot.whenMissing != null) {
                values.set(slot.field, slot.whenMissing);
            }
            return;
        }
        String text = String.join(", ", List.of(parts).subList(position, position + width));
        values.set(slot.field, slot.field.txtCodec().fromText(text, values.get(slot.field), context));
    }

    private TxtTuple<E> with(Slot<E, ?> slot) {
        List<Slot<E, ?>> changed = new ArrayList<>(this.slots);
        changed.add(slot);
        return new TxtTuple<>(this.entry, changed, this.fixed, this.accepts, this.after);
    }

}
