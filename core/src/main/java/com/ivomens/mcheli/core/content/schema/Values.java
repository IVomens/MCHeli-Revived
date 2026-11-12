package com.ivomens.mcheli.core.content.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The settings read from one file so far, by field. A field that was never set reads as its default.
 * This is the only mutable step of the pipeline: the readers fill it, then the schema turns it into an
 * immutable definition.
 * <p>
 * An entry of a list ({@code Seats[2]}) is read into values of its own, which know their place in the list
 * and the values around them, so an entry can derive defaults from its position (a part named
 * {@code hatch2}).
 */
public final class Values {

    private final String name;
    private final int index;
    private final Values parent;
    private final Map<Field<?, ?>, Object> written = new HashMap<>();

    /** @param name the file name without extension, which some defaults depend on */
    public Values(String name) {
        this(name, -1, null);
    }

    private Values(String name, int index, Values parent) {
        this.name = name;
        this.index = index;
        this.parent = parent;
    }

    public String name() {
        return this.name;
    }

    /** Values for the entry at {@code index} of a list held by these values. */
    public Values entry(int index) {
        return new Values(this.name, index, this);
    }

    /** The 0-based place of an entry in its list; -1 for a whole file. */
    public int index() {
        return this.index;
    }

    /** The values of the file or entry that holds this entry. */
    public Optional<Values> parent() {
        return Optional.ofNullable(this.parent);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Field<?, T> field) {
        return this.written.containsKey(field) ? (T) this.written.get(field) : field.defaultFor(this.name);
    }

    public <T> void set(Field<?, T> field, T value) {
        this.written.put(field, value);
    }

    /** Whether the file set the field, even to its default. */
    public boolean isSet(Field<?, ?> field) {
        return this.written.containsKey(field);
    }

    /** Adds one entry to a list setting, such as a legacy {@code AddMuzzleFlash} line. */
    public <E> void append(Field<?, List<E>> field, E element) {
        List<E> list = new ArrayList<>(get(field));
        list.add(element);
        set(field, List.copyOf(list));
    }

    /**
     * Replaces the last entry of a list setting, for legacy lines that add to the entry before them
     * ({@code AddPartWeaponChild} adds a barrel to the last {@code AddPartWeapon}). Does nothing to an empty
     * list.
     */
    public <E> void updateLast(Field<?, List<E>> field, UnaryOperator<E> update) {
        List<E> list = new ArrayList<>(get(field));
        if (!list.isEmpty()) {
            list.set(list.size() - 1, update.apply(list.getLast()));
            set(field, List.copyOf(list));
        }
    }
}
