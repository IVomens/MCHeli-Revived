package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.core.content.schema.EntryList;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.inspector.SnapshotBuilder.SectionWriter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The definition tab: the vehicle file's values, read through its schema the way the emitter writes them,
 * one section per top-level YAML section. A value that differs from the schema's default is marked, and
 * where the file can be read again its line is given.
 */
public final class DefinitionSections {

    /** What the definition tab holds. */
    public enum Detail {
        /** No definition tab: for the client's refresh every tick, which takes the definition once. */
        NONE,
        /** Only the values that differ from the defaults, without lines: what the server sends. */
        CHANGED_ONLY,
        /** Every value, with the file position of those the file sets: what the client shows. */
        ALL_WITH_SOURCES
    }

    /** The section of the settings at the top of the file. */
    public static final String GENERAL = "General";

    private DefinitionSections() {
    }

    static void write(SnapshotBuilder builder, VehicleSpec spec, Detail detail) {
        if (detail == Detail.NONE) {
            return;
        }
        Optional<ContentEntry<VehicleFile>> entry = VehicleTypes.entry(spec);
        SourceLines lines = detail == Detail.ALL_WITH_SOURCES && entry.isPresent() ? SourceLines.of(entry.get())
                : SourceLines.NONE;
        Walk walk = new Walk(builder, spec.name(), detail == Detail.CHANGED_ONLY, lines);
        walk.root(VehicleSchema.forKind(spec.kind()).root(), spec.file());
    }

    /** Walks the schema once, writing a section per top-level YAML section. */
    private static final class Walk {

        private final SnapshotBuilder builder;
        private final String name;
        private final boolean changedOnly;
        private final SourceLines lines;

        Walk(SnapshotBuilder builder, String name, boolean changedOnly, SourceLines lines) {
            this.builder = builder;
            this.name = name;
            this.changedOnly = changedOnly;
            this.lines = lines;
        }

        <D> void root(Section<D, D> root, D file) {
            SectionWriter general = this.builder.section(InspectorTab.DEFINITION, GENERAL, this.lines.file());
            for (Member<D> member : root.members()) {
                if (member instanceof Field<D, ?> field) {
                    field(general, "", field, file);
                }
            }
            for (Member<D> member : root.members()) {
                if (member instanceof Section<D, ?> child) {
                    topSection(child, file);
                }
            }
        }

        private <P, S> void topSection(Section<P, S> section, P parent) {
            SectionWriter out = this.builder.section(InspectorTab.DEFINITION, section.key(), "");
            members(out, section.key() + ".", section, section.valueIn(parent));
        }

        private <S> void members(SectionWriter out, String path, Section<?, S> section, S value) {
            for (Member<S> member : section.members()) {
                switch (member) {
                    case Field<S, ?> field -> field(out, path, field, value);
                    case Section<S, ?> child -> nested(out, path, child, value);
                    case KeyAlias<S> alias -> {
                        // the setting is listed under its own key
                    }
                }
            }
        }

        private <P, S> void nested(SectionWriter out, String path, Section<P, S> child, P parent) {
            S value = parent == null ? null : child.valueIn(parent);
            if (value != null) {
                members(out, path + child.key() + ".", child, value);
            }
        }

        private <S, T> void field(SectionWriter out, String path, Field<S, T> field, S section) {
            if (section == null) {
                return;
            }
            T value = field.valueIn(section);
            T fallback = field.defaultFor(this.name);
            boolean changed = !Objects.equals(value, fallback);
            if (this.changedOnly && !changed) {
                return;
            }
            String key = path.isEmpty() ? field.key() : path.substring(path.indexOf('.') + 1) + field.key();
            int line = this.lines.lineOf(path + field.key(), field.aliases(), field.txtKeys());
            String where = line > 0 ? "line " + line : "";
            String fallbackText = text(field, fallback);
            String detail = changed ? join(where, "default " + (fallbackText.isEmpty() ? "\"\"" : fallbackText))
                    : where;
            out.entry(key, text(field, value), detail, changed);
        }

        private static String join(String first, String second) {
            return first.isEmpty() ? second : first + ", " + second;
        }
    }

    /** A value as the YAML file would spell it; lists of entries are counted. */
    static <T> String text(Field<?, T> field, T value) {
        if (value == null) {
            return "-";
        }
        if (field.codec() instanceof EntryList<?, ?>) {
            return value instanceof Collection<?> list ? list.size() + (list.size() == 1 ? " entry" : " entries")
                    : "set";
        }
        Object yaml = field.codec().toYaml(value);
        if (yaml instanceof Map<?, ?> map) {
            return map.isEmpty() ? "{}" : map.toString();
        }
        if (yaml instanceof List<?> list) {
            return list.toString();
        }
        if (yaml instanceof Double || yaml instanceof Float) {
            return VehicleSnapshots.number(((Number) yaml).doubleValue());
        }
        return String.valueOf(yaml);
    }
}
