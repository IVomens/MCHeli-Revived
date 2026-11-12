package com.ivomens.mcheli.core.content.schema;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * One setting of a content file: its YAML key, its legacy TXT keys, how its value is spelled, its default,
 * and where the definition record keeps it. Both readers and the emitter work from this declaration.
 * <p>
 * Fields are immutable; each configuration method returns a changed copy, so a declaration reads as one
 * chain: {@code Field.of("Acceleration", Codecs.floats(0, 100), 1.0F, Ballistics::acceleration)
 * .alias("Accel").txt("acceleration")}.
 *
 * @param <S> the record of the section that holds the value
 * @param <T> the value type
 */
public final class Field<S, T> implements Member<S> {

    private final String key;
    private final List<String> aliases;
    private final ValueCodec<T> codec;
    private final Function<String, T> defaultForName;
    private final Function<S, T> getter;
    private final List<String> txtKeys;
    private final ValueCodec<T> txtCodec;
    /** Null when TXT values need no conversion. */
    private final UnaryOperator<T> afterTxt;
    private final boolean alwaysWritten;

    private Field(String key, List<String> aliases, ValueCodec<T> codec, Function<String, T> defaultForName,
                  Function<S, T> getter, List<String> txtKeys, ValueCodec<T> txtCodec, UnaryOperator<T> afterTxt,
                  boolean alwaysWritten) {
        this.key = key;
        this.aliases = List.copyOf(aliases);
        this.codec = codec;
        this.defaultForName = defaultForName;
        this.getter = getter;
        this.txtKeys = List.copyOf(txtKeys);
        this.txtCodec = txtCodec;
        this.afterTxt = afterTxt;
        this.alwaysWritten = alwaysWritten;
    }

    /** A setting with a fixed default; {@code defaultValue} may be null for an optional value. */
    public static <S, T> Field<S, T> of(String key, ValueCodec<T> codec, T defaultValue, Function<S, T> getter) {
        return named(key, codec, name -> defaultValue, getter);
    }

    /** A setting whose default depends on the file name, such as the display name. */
    public static <S, T> Field<S, T> named(String key, ValueCodec<T> codec, Function<String, T> defaultForName,
                                           Function<S, T> getter) {
        return new Field<>(key, List.of(), codec, defaultForName, getter, List.of(), codec, null, false);
    }

    /**
     * A YAML list whose entries have their own keys, such as {@code Seats}; it defaults to an empty list. The
     * entry schema is looked up when first needed, so an entry may hold a list of its own kind.
     */
    public static <S, E> Field<S, List<E>> listOf(String key, Supplier<EntrySchema<E>> entries,
                                                  Function<S, List<E>> getter) {
        return listOf(key, entries, List.of(), getter);
    }

    /** A YAML list whose entries have their own keys, with a default other than the empty list. */
    public static <S, E> Field<S, List<E>> listOf(String key, Supplier<EntrySchema<E>> entries, List<E> defaults,
                                                  Function<S, List<E>> getter) {
        return of(key, new EntryList.Many<>(entries), List.copyOf(defaults), getter);
    }

    /** A YAML map with its own keys, such as a seat's {@code Camera}; null when the file leaves it out. */
    public static <S, E> Field<S, E> entry(String key, Supplier<EntrySchema<E>> entry, Function<S, E> getter) {
        return of(key, new EntryList.One<>(entry), null, getter);
    }

    /** Other YAML spellings of the key. */
    public Field<S, T> alias(String... names) {
        return new Field<>(this.key, List.of(names), this.codec, this.defaultForName, this.getter, this.txtKeys,
                this.txtCodec, this.afterTxt, this.alwaysWritten);
    }

    /** The TXT keys (any case) that set this value, spelled like the YAML value. */
    public Field<S, T> txt(String... keys) {
        return txt(this.codec, keys);
    }

    /**
     * The TXT keys that set this value, with the legacy TXT spelling where it differs from YAML (usually a
     * different range; both are kept so old files load exactly as they always did).
     */
    public Field<S, T> txt(ValueCodec<T> legacyCodec, String... keys) {
        List<String> lowerCase = List.of(keys).stream().map(k -> k.toLowerCase(Locale.ROOT)).toList();
        return new Field<>(this.key, this.aliases, this.codec, this.defaultForName, this.getter, lowerCase,
                legacyCodec, this.afterTxt, this.alwaysWritten);
    }

    /**
     * A conversion applied once a TXT file has been read, to the final value whether it was written or
     * not, such as seconds to ticks.
     */
    public Field<S, T> afterTxt(UnaryOperator<T> conversion) {
        return new Field<>(this.key, this.aliases, this.codec, this.defaultForName, this.getter, this.txtKeys,
                this.txtCodec, conversion, this.alwaysWritten);
    }

    /** The emitter writes the value even when it is the default, so converted files show it. */
    public Field<S, T> alwaysWritten() {
        return new Field<>(this.key, this.aliases, this.codec, this.defaultForName, this.getter, this.txtKeys,
                this.txtCodec, this.afterTxt, true);
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public List<String> aliases() {
        return this.aliases;
    }

    public ValueCodec<T> codec() {
        return this.codec;
    }

    public T defaultFor(String name) {
        return this.defaultForName.apply(name);
    }

    public T valueIn(S section) {
        return this.getter.apply(section);
    }

    public List<String> txtKeys() {
        return this.txtKeys;
    }

    public ValueCodec<T> txtCodec() {
        return this.txtCodec;
    }

    /** The value a finished TXT file ends up with. */
    public T afterTxt(T value) {
        return this.afterTxt == null ? value : this.afterTxt.apply(value);
    }

    public boolean isAlwaysWritten() {
        return this.alwaysWritten;
    }

    @Override
    public String toString() {
        return "Field[" + this.key + "]";
    }
}
