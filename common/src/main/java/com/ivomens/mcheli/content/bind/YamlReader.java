package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.EntryList;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.source.ContentParseException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a {@code .yml} content file through its {@link Schema}. Keys are matched section by section; an
 * unknown key is reported (with a suggestion when it looks like a typo) and skipped, a key the game no
 * longer reads is reported once per load, and a value that cannot be used fails the file with its key path
 * and line. Lists of entries ({@code Seats}) are read entry by entry through their own schema, with paths
 * such as {@code Seats[2].Camera.Pos}.
 * <p>
 * Files that are not one schema, such as HUD programs, walk the document themselves and read each map of
 * keys through an {@link EntrySchema} with a reader made by {@link #of}.
 */
public final class YamlReader {

    private final List<String> lines;
    private final String file;
    private final SourceWarnings warnings;

    private YamlReader(List<String> lines, String file) {
        this.lines = lines;
        this.file = file;
        this.warnings = new SourceWarnings(file);
    }

    /**
     * @param name  the file name without extension
     * @param file  the path inside the pack, for messages
     * @param lines the file's text
     */
    public static <D extends ContentDefinition> D read(Schema<D> schema, String name, String file,
                                                       List<String> lines) {
        Map<String, Object> root = loadDocument(lines);
        Values values = new Values(name);
        YamlReader reader = new YamlReader(lines, file);
        if (root.containsKey(Schema.VERSION_KEY)) {
            root = new LinkedHashMap<>(root);
            reader.checkVersion(root.remove(Schema.VERSION_KEY));
        }
        try {
            reader.readSection(schema.root(), root, values, "", "");
        } catch (ContentParseException e) {
            throw e.getKeyPath() == null ? e : e.atLine(YamlLineLocator.find(lines, e.getKeyPath()));
        }
        return schema.build(values);
    }

    /**
     * Checks the version a file says it is written for. Version 1 is the only one so far, so there is nothing
     * to migrate; a file written for a later version is read as it is, and says so.
     */
    private void checkVersion(Object raw) {
        if (!(raw instanceof Integer version)) {
            warn(Schema.VERSION_KEY, "expected a whole number but got " + Codecs.describe(raw)
                    + ", the file is read as version " + Schema.VERSION);
        } else if (version > Schema.VERSION) {
            warn(Schema.VERSION_KEY, "the file is written for version " + version + " but this game reads version "
                    + Schema.VERSION + "; keys it does not know are ignored");
        } else if (version < 1) {
            warn(Schema.VERSION_KEY, "versions start at 1, the file is read as version " + Schema.VERSION);
        }
    }

    /**
     * A reader for one file, for content that walks its document itself and reads its parts with
     * {@link #entry} and {@link #value}.
     */
    public static YamlReader of(String file, List<String> lines) {
        return new YamlReader(lines, file);
    }

    /**
     * Reads one map of keys through its entry schema, or the plain value of the entry's short form.
     *
     * @param values  the values of the entry, made with {@link Values#entry}
     * @param keyPath where the map is, such as {@code [3].DrawLine}, for messages and line numbers
     */
    public <E> E entry(EntrySchema<E> schema, Object raw, Values values, String keyPath) {
        try {
            return readEntry(schema, raw, values, keyPath);
        } catch (ContentParseException e) {
            throw atLine(e);
        }
    }

    /** Reads one plain value, such as a HUD condition. */
    public <T> T value(ValueCodec<T> codec, Object raw, String keyPath) {
        try {
            return codec.fromYaml(raw, null, message -> warn(keyPath, message));
        } catch (ContentValueException e) {
            throw atLine(new ContentParseException(e.getMessage(), keyPath, ContentParseException.UNKNOWN_LINE, e));
        } catch (RuntimeException e) {
            throw atLine(new ContentParseException(ContentParseException.describe(e), keyPath,
                    ContentParseException.UNKNOWN_LINE, e));
        }
    }

    /** Logs a problem that does not stop the file from loading, with the line of {@code keyPath}. */
    public void warn(String keyPath, String message) {
        this.warnings.warn(line(keyPath), keyPath, message);
    }

    /** Logs a key nothing reads, suggesting the closest of the {@code known} keys after a typo. */
    public void unknownKey(String keyPath, String key, Collection<String> known) {
        this.warnings.unknownKey(line(keyPath), keyPath, key, known);
    }

    /** A problem that stops the file from loading, with the line of {@code keyPath}. */
    public ContentParseException error(String message, String keyPath) {
        return new ContentParseException(message, keyPath, line(keyPath), null);
    }

    private ContentParseException atLine(ContentParseException e) {
        return e.getLineNo() != ContentParseException.UNKNOWN_LINE || e.getKeyPath() == null ? e
                : e.atLine(line(e.getKeyPath()));
    }

    /** Parses the text; the top of a content file must be a map of keys. */
    public static Map<String, Object> loadDocument(List<String> lines) {
        Object document = load(lines);
        if (document instanceof Map<?, ?>) {
            return Codecs.map(document);
        }
        throw new ContentParseException(document == null ? "the file is empty"
                : "the top level of the file must be a map of keys", 1);
    }

    /** Parses YAML text, turning a syntax error into a message with its line. */
    public static Object load(List<String> lines) {
        try {
            return new Yaml().load(String.join("\n", lines));
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark();
            int line = mark != null ? mark.getLine() + 1 : ContentParseException.UNKNOWN_LINE;
            throw new ContentParseException("YAML syntax error: " + e.getProblem(), null, line, e);
        }
    }

    /**
     * @param written   the path as the file spells it, for messages and line lookup
     * @param canonical the path in the schema's own spelling, for the unsupported-settings report
     */
    private <S> void readSection(Section<?, S> section, Map<String, Object> map, Values values, String written,
                                 String canonical) {
        section.enterYaml(values);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String keyPath = join(written, key);
            Optional<Member<S>> member = section.member(key);
            if (member.isPresent()) {
                readMember(member.get(), entry.getValue(), values, keyPath, canonical);
            } else if (section.isUnsupported(key)) {
                UnsupportedSettings.report(join(canonical, key), this.file);
            } else {
                this.warnings.unknownKey(line(keyPath), keyPath, key, section.knownKeys());
            }
        }
        for (Field<S, ?> field : section.required()) {
            if (!values.isSet(field)) {
                throw new ContentParseException("the section needs a '" + field.key() + "' key", written,
                        ContentParseException.UNKNOWN_LINE, null);
            }
        }
        section.leaveYaml(values);
    }

    private <S> void readMember(Member<S> member, Object raw, Values values, String keyPath, String canonical) {
        switch (member) {
            case Field<S, ?> field -> readField(field, raw, values, keyPath);
            case KeyAlias<S> alias -> readAlias(alias, raw, values, keyPath);
            case Section<S, ?> child -> readChild(child, raw, values, keyPath, join(canonical, child.key()));
        }
    }

    private <S> void readChild(Section<S, ?> child, Object raw, Values values, String keyPath, String canonical) {
        if (raw instanceof Map<?, ?> || child.shorthand().isEmpty() && !child.isShorthandUnsupported()) {
            readSection(child, sectionMap(raw, keyPath), values, keyPath, canonical);
        } else if (child.isShorthandUnsupported()) {
            UnsupportedSettings.report(canonical, this.file);
        } else {
            readField(child.shorthand().get(), raw, values, keyPath);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void readAlias(KeyAlias<?> alias, Object raw, Values values, String keyPath) {
        readField((Field<?, T>) alias.target(), (ValueCodec<T>) alias.spelling(), raw, values, keyPath);
    }

    private <T> void readField(Field<?, T> field, Object raw, Values values, String keyPath) {
        readField(field, field.codec(), raw, values, keyPath);
    }

    private <T> void readField(Field<?, T> field, ValueCodec<T> codec, Object raw, Values values, String keyPath) {
        if (codec instanceof EntryList.Many<?> entries) {
            values.set(field, readEntries(entries, raw, values, keyPath));
            return;
        }
        if (codec instanceof EntryList.One<?> entry) {
            values.set(field, readEntry(entry.schema(), raw, values.entry(0), keyPath));
            return;
        }
        try {
            T value = codec.fromYaml(raw, values.get(field), message ->
                    this.warnings.warn(line(keyPath), keyPath, message));
            values.set(field, value);
        } catch (ContentParseException e) {
            throw e;
        } catch (ContentValueException e) {
            throw new ContentParseException(e.getMessage(), keyPath, ContentParseException.UNKNOWN_LINE, e);
        } catch (RuntimeException e) {
            throw new ContentParseException(ContentParseException.describe(e), keyPath,
                    ContentParseException.UNKNOWN_LINE, e);
        }
    }

    /** Reads every entry of a list through its schema; entries are numbered from 1 in paths. */
    @SuppressWarnings("unchecked")
    private <T, E> T readEntries(EntryList.Many<E> entries, Object raw, Values values, String keyPath) {
        EntrySchema<E> schema = entries.schema();
        List<?> list = list(raw, keyPath);
        List<E> read = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            read.add(readEntry(schema, list.get(i), values.entry(i), keyPath + "[" + (i + 1) + "]"));
        }
        return (T) List.copyOf(read);
    }

    /** Reads an entry's map, or the plain value of its short form. */
    @SuppressWarnings("unchecked")
    private <T, E> T readEntry(EntrySchema<E> schema, Object raw, Values entry, String entryPath) {
        Section<E, E> root = schema.root();
        if (!(raw instanceof Map<?, ?>) && root.shorthand().isPresent()) {
            root.enterYaml(entry);
            readField(root.shorthand().get(), raw, entry, entryPath);
            root.leaveYaml(entry);
        } else {
            readSection(root, sectionMap(raw, entryPath), entry, entryPath, canonicalEntry(entryPath));
        }
        try {
            return (T) schema.build(entry);
        } catch (ContentValueException e) {
            throw new ContentParseException(e.getMessage(), entryPath, ContentParseException.UNKNOWN_LINE, e);
        }
    }

    /** {@code Seats[2].Camera} as the unsupported-settings report spells it: {@code Seats[].Camera}. */
    private static String canonicalEntry(String entryPath) {
        return entryPath.replaceAll("\\[\\d+]", "[]");
    }

    private static List<?> list(Object raw, String keyPath) {
        try {
            return Codecs.list(raw);
        } catch (ContentValueException e) {
            throw new ContentParseException(e.getMessage(), keyPath, ContentParseException.UNKNOWN_LINE, e);
        }
    }

    private static Map<String, Object> sectionMap(Object raw, String keyPath) {
        try {
            return Codecs.map(raw);
        } catch (ContentValueException e) {
            throw new ContentParseException(e.getMessage(), keyPath, ContentParseException.UNKNOWN_LINE, e);
        }
    }

    /** The line of a key path such as {@code [3].DrawRectangle}, or {@link ContentParseException#UNKNOWN_LINE}. */
    public int line(String keyPath) {
        return YamlLineLocator.find(this.lines, keyPath);
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
