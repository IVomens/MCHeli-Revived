package com.ivomens.mcheli.core.content.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A YAML section, such as {@code Ballistics}, with its keys in the order the emitter writes them. The root
 * of a file is a section too. Sections are immutable; each configuration method returns a changed copy.
 *
 * @param <P> the record of the enclosing section
 * @param <S> the record this section is read into
 */
public final class Section<P, S> implements Member<P> {

    private final String key;
    private final List<String> aliases;
    private final Function<P, S> getter;
    private final List<Member<S>> members;
    private final List<String> unsupported;
    private final Rules<S> rules;
    private final Map<String, Member<S>> byKey = new HashMap<>();

    /**
     * The rare legacy behaviour of a section.
     *
     * @param onYamlEntry        what entering the section sets, or null
     * @param afterYaml          what leaving the section checks or changes, or null
     * @param required           keys the section cannot do without
     * @param shorthand          the setting a plain value instead of a map sets, or null
     * @param shorthandUnsupported whether a plain value is an old spelling the game no longer reads
     */
    private record Rules<S>(Consumer<Values> onYamlEntry, Consumer<Values> afterYaml, List<Field<S, ?>> required,
                            Field<?, ?> shorthand, boolean shorthandUnsupported) {
    }

    private Section(String key, List<String> aliases, Function<P, S> getter, List<Member<S>> members,
                    List<String> unsupported, Rules<S> rules) {
        this.key = key;
        this.aliases = List.copyOf(aliases);
        this.getter = getter;
        this.members = List.copyOf(members);
        this.unsupported = List.copyOf(unsupported);
        this.rules = rules;
        for (Member<S> member : this.members) {
            this.byKey.put(member.key(), member);
            member.aliases().forEach(alias -> this.byKey.put(alias, member));
        }
    }

    @SafeVarargs
    public static <P, S> Section<P, S> of(String key, Function<P, S> getter, Member<S>... members) {
        return new Section<>(key, List.of(), getter, List.of(members), List.of(),
                new Rules<>(null, null, List.of(), null, false));
    }

    /** The top of a file. */
    @SafeVarargs
    public static <D> Section<D, D> root(Member<D>... members) {
        return root(List.of(members));
    }

    /** The top of a file or of a list entry, with its keys in emitter order. */
    public static <D> Section<D, D> root(List<Member<D>> members) {
        return new Section<>("", List.of(), Function.identity(), members, List.of(),
                new Rules<>(null, null, List.of(), null, false));
    }

    /** Other YAML spellings of the section name. */
    public Section<P, S> alias(String... names) {
        return new Section<>(this.key, List.of(names), this.getter, this.members, this.unsupported, this.rules);
    }

    /** Keys older packs still write but the game no longer reads; see docs/CONTENT_FORMAT_NOTES.md. */
    public Section<P, S> unsupported(String... keys) {
        List<String> all = new ArrayList<>(this.unsupported);
        all.addAll(List.of(keys));
        return new Section<>(this.key, this.aliases, this.getter, this.members, all, this.rules);
    }

    /** Runs when the YAML reader enters the section, before its keys are read. */
    public Section<P, S> onYamlEntry(Consumer<Values> rule) {
        return withRules(new Rules<>(rule, this.rules.afterYaml, this.rules.required, this.rules.shorthand,
                this.rules.shorthandUnsupported));
    }

    /**
     * The section switches a feature on by being there, such as {@code AircraftFeatures.Radar}: entering it
     * sets {@code flag}, which the section also accepts as its {@code Enabled} key, and the emitter writes
     * the section, empty if need be, whenever the flag is on.
     */
    public Section<P, S> enabledBy(Field<S, Boolean> flag) {
        return onYamlEntry(values -> values.set(flag, true));
    }

    /** Runs when the YAML reader leaves the section, after its keys are read. */
    public Section<P, S> afterYaml(Consumer<Values> rule) {
        return withRules(new Rules<>(this.rules.onYamlEntry, rule, this.rules.required, this.rules.shorthand,
                this.rules.shorthandUnsupported));
    }

    /**
     * A key the YAML section cannot do without: a section that leaves it out fails the file, and the emitter
     * leaves the whole section out while the value is null.
     */
    public Section<P, S> requires(Field<S, ?> field) {
        List<Field<S, ?>> required = new ArrayList<>(this.rules.required);
        required.add(field);
        return withRules(new Rules<>(this.rules.onYamlEntry, this.rules.afterYaml, List.copyOf(required),
                this.rules.shorthand, this.rules.shorthandUnsupported));
    }

    /**
     * A plain value in place of the section's map sets {@code field}, without entering the section:
     * {@code Parachuting: true} or {@code FuelSupply: 12}. The emitter uses the short form when the field is
     * all the section has to say.
     */
    public Section<P, S> shorthand(Field<?, ?> field) {
        return withRules(new Rules<>(this.rules.onYamlEntry, this.rules.afterYaml, this.rules.required, field,
                false));
    }

    /**
     * A plain value in place of the section's map is an old spelling the game no longer reads, such as
     * {@code Maintenance: true}; it is reported once per load like the other unsupported settings.
     */
    public Section<P, S> shorthandUnsupported() {
        return withRules(new Rules<>(this.rules.onYamlEntry, this.rules.afterYaml, this.rules.required, null,
                true));
    }

    private Section<P, S> withRules(Rules<S> changed) {
        return new Section<>(this.key, this.aliases, this.getter, this.members, this.unsupported, changed);
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public List<String> aliases() {
        return this.aliases;
    }

    public List<Member<S>> members() {
        return this.members;
    }

    /** The member a YAML key names, by its key or an alias. */
    public Optional<Member<S>> member(String yamlKey) {
        return Optional.ofNullable(this.byKey.get(yamlKey));
    }

    public boolean isUnsupported(String yamlKey) {
        return this.unsupported.contains(yamlKey);
    }

    /** The keys older packs still write but the game no longer reads. */
    public List<String> unsupportedKeys() {
        return this.unsupported;
    }

    /** Every key the reader accepts here, for suggestions after a typo. */
    public List<String> knownKeys() {
        return List.copyOf(this.byKey.keySet());
    }

    public S valueIn(P parent) {
        return this.getter.apply(parent);
    }

    public void enterYaml(Values values) {
        if (this.rules.onYamlEntry != null) {
            this.rules.onYamlEntry.accept(values);
        }
    }

    /** Whether entering the section changes values, which makes even an empty section say something. */
    public boolean setsValuesOnEntry() {
        return this.rules.onYamlEntry != null;
    }

    public void leaveYaml(Values values) {
        if (this.rules.afterYaml != null) {
            this.rules.afterYaml.accept(values);
        }
    }

    /** The keys the section cannot do without. */
    public List<Field<S, ?>> required() {
        return this.rules.required;
    }

    /** The setting a plain value in place of the map sets, if the section has a short form. */
    public Optional<Field<?, ?>> shorthand() {
        return Optional.ofNullable(this.rules.shorthand);
    }

    /** Whether a plain value in place of the map is an old spelling the game no longer reads. */
    public boolean isShorthandUnsupported() {
        return this.rules.shorthandUnsupported;
    }

    @Override
    public String toString() {
        return "Section[" + this.key + "]";
    }
}
