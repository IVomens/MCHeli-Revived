package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.core.content.def.hud.HudCommand.TxtForm;
import com.ivomens.mcheli.core.content.def.hud.HudCommands;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.source.ContentParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads a legacy {@code .txt} HUD script: one {@code command = values} per line, keys in any case, looked up
 * in {@link HudCommands}. {@code if = condition} opens a block that {@code endif} closes; blocks cannot nest.
 * {@code endif} and {@code exit} may be written without a value. Lines without {@code =} are comments.
 */
public final class HudTxtReader {

    private static final String IF = "if";
    private static final String ENDIF = "endif";
    private static final String EXIT = "exit";

    private final String file;
    private final Values values;
    private final SourceWarnings warnings;
    private final List<HudOp> program = new ArrayList<>();
    /** The open {@code if} block, or null. */
    private OpenIf openIf;

    private record OpenIf(int line, String condition, List<HudOp> body) {
    }

    private HudTxtReader(String name, String file) {
        this.file = file;
        this.values = new Values(name);
        this.warnings = new SourceWarnings(file);
    }

    /**
     * @param name the file name without extension
     * @param file the path inside the pack, for messages
     */
    public static HudProgram read(String name, String file, List<String> lines) {
        TxtReader.warnDeprecated(file);
        HudTxtReader reader = new HudTxtReader(name, file);
        for (int i = 0; i < lines.size(); i++) {
            reader.readLine(i + 1, lines.get(i).trim());
        }
        if (reader.openIf != null) {
            throw new ContentParseException("the if on line " + reader.openIf.line + " has no endif", IF,
                    reader.openIf.line, null);
        }
        return new HudProgram(name, false, reader.program);
    }

    private void readLine(int lineNumber, String text) {
        String line = text.equalsIgnoreCase(ENDIF) || text.equalsIgnoreCase(EXIT) ? text + "=0" : text;
        int separator = line.indexOf('=');
        if (separator < 0 || line.length() <= separator + 1) {
            return;
        }
        String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = line.substring(separator + 1).trim();
        try {
            switch (key) {
                case IF -> openIf(lineNumber, value);
                case ENDIF -> closeIf(lineNumber);
                default -> readCommand(lineNumber, key, value);
            }
        } catch (ContentParseException e) {
            throw e;
        } catch (ContentValueException e) {
            throw new ContentParseException(e.getMessage(), key, lineNumber, e);
        } catch (RuntimeException e) {
            throw new ContentParseException(ContentParseException.describe(e) + " (value '" + value + "')", key,
                    lineNumber, e);
        }
    }

    /** The condition is the first value; the legacy parser split the line at commas like any other. */
    private void openIf(int lineNumber, String value) {
        if (this.openIf != null) {
            throw new ContentValueException("if blocks cannot nest, and the if on line " + this.openIf.line
                    + " has no endif before this one");
        }
        this.openIf = new OpenIf(lineNumber, Codecs.splitTxt(value)[0], new ArrayList<>());
    }

    private void closeIf(int lineNumber) {
        if (this.openIf == null) {
            throw new ContentValueException("endif without an if before it");
        }
        this.program.add(new HudOp.If(this.openIf.line, this.openIf.condition, this.openIf.body, lineNumber));
        this.openIf = null;
    }

    private void readCommand(int lineNumber, String key, String value) {
        Optional<TxtForm<?>> form = HudCommands.txt(key);
        if (form.isEmpty()) {
            this.warnings.unknownKey(lineNumber, key, key, HudCommands.txtKeys());
            return;
        }
        Values entry = this.values.entry(this.program.size());
        entry.set(HudCommands.LINE, lineNumber);
        ReadContext context = message -> this.warnings.warn(lineNumber, key, message);
        form.get().values().read(value, entry, context)
                .ifPresent(op -> (this.openIf != null ? this.openIf.body : this.program).add(op));
    }
}
