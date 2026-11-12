package com.ivomens.mcheli.content.emit;

import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.EntryList;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes a definition as YAML by walking its schema: keys in declaration order, values the reader would get
 * anyway left out (unless the field is always written), sections that only hold defaults left out. Because
 * every value goes through the same codec the reader uses, reading the output gives the same definition back.
 */
public final class SchemaEmitter {

    private SchemaEmitter() {
    }

    public static <D extends ContentDefinition> String emit(Schema<D> schema, D definition) {
        return write(tree(schema, definition));
    }

    /** The YAML document as nested maps, before it is written as text. */
    public static <D extends ContentDefinition> Map<String, Object> tree(Schema<D> schema, D definition) {
        return writeSection(schema.root(), definition, new Values(definition.name()));
    }

    /**
     * One entry as its map of keys, or as the plain value of its short form when that says everything, for
     * documents that are not one schema, such as a HUD program.
     *
     * @param name the file name without extension, which some defaults depend on
     */
    public static <E> Object entry(EntrySchema<E> schema, E entry, String name) {
        return writeEntry(schema, entry, new Values(name).entry(0));
    }

    /** Writes a document built from maps, lists and plain values, with {@link InlineList}s on one line. */
    public static String write(Object document) {
        return yaml().dump(document);
    }

    /**
     * @param unwritten what the reader holds for a key the file leaves out: the default, or what entering the
     *                  section sets (a YAML Recoil section starts its buffer at 1)
     */
    private static <S> Map<String, Object> writeSection(Section<?, S> section, S value, Values unwritten) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Member<S> member : section.members()) {
            switch (member) {
                case Field<S, ?> field -> writeField(field, value, unwritten, map);
                case Section<S, ?> child -> writeChild(child, value, unwritten, map);
                case KeyAlias<S> alias -> {
                    // only the setting's own key is written
                }
            }
        }
        return map;
    }

    private static <S, C> void writeChild(Section<S, C> child, S parent, Values unwritten, Map<String, Object> map) {
        C value = child.valueIn(parent);
        boolean missingRequired = child.required().stream().anyMatch(field -> field.valueIn(value) == null);
        if (missingRequired || !differsFromDefaults(child, value, unwritten.name())) {
            return;
        }
        Optional<Object> shorthand = shorthand(child, value, unwritten);
        if (shorthand.isPresent()) {
            map.put(child.key(), shorthand.get());
            return;
        }
        child.enterYaml(unwritten);
        Map<String, Object> written = writeSection(child, value, unwritten);
        if (!written.isEmpty() || child.setsValuesOnEntry()) {
            map.put(child.key(), written);
        }
    }

    /**
     * The short form of a section ({@code Parachuting: true}, {@code FuelSupply: 12}) when its short field
     * is all it has to say: every other member holds what entering the section gives anyway.
     */
    private static <S, C> Optional<Object> shorthand(Section<S, C> child, C value, Values unwritten) {
        if (child.shorthand().isEmpty()) {
            return Optional.empty();
        }
        Values entered = new Values(unwritten.name());
        child.enterYaml(entered);
        Map<String, Object> rest = writeSection(child, value, entered);
        Field<?, ?> shorthand = child.shorthand().get();
        rest.remove(shorthand.key());
        return rest.isEmpty() ? Optional.ofNullable(shorthandValue(shorthand, child, value)) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <C, T> Object shorthandValue(Field<?, T> shorthand, Section<?, C> child, C value) {
        T held = child.members().contains(shorthand) ? ((Field<C, T>) shorthand).valueIn(value) : null;
        return held == null ? null : shorthand.codec().toYaml(held);
    }

    /** Whether the section says anything a file without it would not. */
    private static <S> boolean differsFromDefaults(Section<?, S> section, S value, String name) {
        for (Member<S> member : section.members()) {
            boolean differs = switch (member) {
                case Field<S, ?> field -> field.isAlwaysWritten() || !isDefault(field, value, name);
                case Section<S, ?> child -> childDiffers(child, value, name);
                case KeyAlias<S> alias -> false;
            };
            if (differs) {
                return true;
            }
        }
        return false;
    }

    private static <S, C> boolean childDiffers(Section<S, C> child, S parent, String name) {
        return differsFromDefaults(child, child.valueIn(parent), name);
    }

    private static <S, T> boolean isDefault(Field<S, T> field, S section, String name) {
        return Objects.equals(field.valueIn(section), field.defaultFor(name));
    }

    private static <S, T> void writeField(Field<S, T> field, S section, Values unwritten, Map<String, Object> map) {
        T value = field.valueIn(section);
        if (value == null || !field.isAlwaysWritten() && Objects.equals(value, unwritten.get(field))) {
            return;
        }
        Object written = switch (field.codec()) {
            case EntryList.Many<?> entries -> writeEntries(entries.schema(), (List<?>) value, unwritten);
            case EntryList.One<?> entry -> writeEntry(entry.schema(), value, unwritten.entry(0));
            default -> field.codec().toYaml(value);
        };
        if (written != null) {
            map.put(field.key(), written);
        }
    }

    private static <E> List<Object> writeEntries(EntrySchema<E> schema, List<?> entries, Values unwritten) {
        List<Object> written = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            written.add(writeEntry(schema, entries.get(i), unwritten.entry(i)));
        }
        return written;
    }

    @SuppressWarnings("unchecked")
    private static <E> Object writeEntry(EntrySchema<E> schema, Object entry, Values unwritten) {
        Optional<Object> shorthand = shorthand(schema.root(), (E) entry, unwritten);
        if (shorthand.isPresent()) {
            return shorthand.get();
        }
        schema.root().enterYaml(unwritten);
        return writeSection(schema.root(), (E) entry, unwritten);
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setWidth(Integer.MAX_VALUE);
        options.setSplitLines(false);
        return new Yaml(new InlineListRepresenter(options), options);
    }

    private static final class InlineListRepresenter extends Representer {

        InlineListRepresenter(DumperOptions options) {
            super(options);
        }

        @Override
        protected Node representSequence(Tag tag, Iterable<?> sequence, DumperOptions.FlowStyle flowStyle) {
            return super.representSequence(tag, sequence,
                    sequence instanceof InlineList ? DumperOptions.FlowStyle.FLOW : flowStyle);
        }
    }
}
