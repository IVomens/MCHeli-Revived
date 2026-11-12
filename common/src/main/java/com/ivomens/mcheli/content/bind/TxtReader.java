package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.TxtKey;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.source.ContentParseException;
import com.ivomens.mcheli.util.ModLogger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads a legacy {@code .txt} content file through its {@link Schema}: one {@code key = value} per line,
 * keys in any case, lines without a value ignored. Lines are applied in file order, because some legacy
 * keys change other values as a side effect (see the schema's TXT rules).
 */
public final class TxtReader {

    private final Schema<?> schema;
    private final Values values;
    private final String file;
    private final SourceWarnings warnings;
    private final boolean reload;

    private TxtReader(Schema<?> schema, Values values, String file, boolean reload) {
        this.schema = schema;
        this.values = values;
        this.file = file;
        this.warnings = new SourceWarnings(file);
        this.reload = reload;
    }

    /**
     * @param name   the file name without extension
     * @param file   the path inside the pack, for messages
     * @param reload true when an entry is read again in game; only the keys the schema reloads are read then
     *               (none for weapons and throwables), see {@link Schema#readsOnReload}
     */
    public static <D extends ContentDefinition> D read(Schema<D> schema, String name, String file,
                                                       List<String> lines, boolean reload) {
        warnDeprecated(file);
        Values values = new Values(name);
        TxtReader reader = new TxtReader(schema, values, file, reload);
        for (int i = 0; i < lines.size(); i++) {
            reader.readLine(i + 1, lines.get(i).trim());
        }
        schema.finishTxt(values);
        schema.fields().forEach(field -> finish(field, values));
        return schema.build(values);
    }

    public static void warnDeprecated(String file) {
        ModLogger.get().warn("Loading legacy .txt content '{}': the TXT format is deprecated, please convert it "
                + "to YAML (the YAML emitter can convert legacy and Reforged packs).", file);
    }

    private void readLine(int lineNumber, String line) {
        int separator = line.indexOf('=');
        if (separator < 0 || line.length() <= separator + 1) {
            return;
        }
        String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        if (this.reload && !this.schema.readsOnReload(key)) {
            return;
        }
        String value = line.substring(separator + 1).trim();
        ReadContext context = message -> this.warnings.warn(lineNumber, key, message);
        Optional<TxtKey> meaning = this.schema.txtKey(key);
        if (meaning.isEmpty()) {
            this.warnings.unknownKey(lineNumber, key, key, this.schema.txtKeys());
            return;
        }
        try {
            switch (meaning.get()) {
                case TxtKey.SetsField(Field<?, ?> field) -> set(field, value, context);
                case TxtKey.AppliesRule(TxtKey.TxtRule rule) -> rule.apply(this.values, value, context);
                case TxtKey.Unsupported() -> UnsupportedSettings.report(key, this.file);
            }
        } catch (ContentValueException e) {
            throw new ContentParseException(e.getMessage(), key, lineNumber, e);
        } catch (RuntimeException e) {
            throw new ContentParseException(ContentParseException.describe(e) + " (value '" + value + "')", key,
                    lineNumber, e);
        }
    }

    private <T> void set(Field<?, T> field, String text, ReadContext context) {
        this.values.set(field, field.txtCodec().fromText(text, this.values.get(field), context));
    }

    private static <T> void finish(Field<?, T> field, Values values) {
        T value = values.get(field);
        T converted = field.afterTxt(value);
        if (converted != value) {
            values.set(field, converted);
        }
    }
}
