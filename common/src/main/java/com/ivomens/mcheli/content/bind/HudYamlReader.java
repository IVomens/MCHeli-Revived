package com.ivomens.mcheli.content.bind;

import com.ivomens.mcheli.core.content.def.hud.HudCommand;
import com.ivomens.mcheli.core.content.def.hud.HudCommands;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.source.ContentParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads a {@code .yml} HUD script: a list of commands, each a one-key map such as
 * {@code - DrawRectangle: {Position: [0, 0], Size: [4, 4]}}, whose keys are read through the command's entry
 * schema in {@link HudCommands}. Unknown commands and keys are reported and skipped; a value that cannot be
 * used fails the file with its key path and line. Every command keeps the line it is on.
 * <p>
 * Blocks depend on the format. In the first format {@code - If: condition} with {@code Do: [commands]}
 * guards a block, and blocks do not nest: an {@code If} inside {@code Do} is reported and skipped. A file that
 * starts with {@code - SchemaVersion: 2} is in format 2, where {@code If} takes {@code Then} (or {@code Do})
 * and an optional {@code Else}, and both may hold blocks of their own.
 */
public final class HudYamlReader {

    private static final String IF = "If";
    private static final String DO = "Do";
    private static final String THEN = "Then";
    private static final String ELSE = "Else";

    private final YamlReader reader;
    private final Values values;
    private final int formatVersion;
    private boolean ignoreAutoScale;

    private HudYamlReader(YamlReader reader, String name, int formatVersion) {
        this.reader = reader;
        this.values = new Values(name);
        this.formatVersion = formatVersion;
    }

    /**
     * @param name the file name without extension
     * @param file the path inside the pack, for messages
     */
    public static HudProgram read(String name, String file, List<String> lines) {
        Object document = YamlReader.load(lines);
        if (!(document instanceof List<?> items)) {
            throw new ContentParseException("a HUD file is a list of commands, such as '- DrawRectangle: ...'",
                    ContentParseException.UNKNOWN_LINE);
        }
        YamlReader yaml = YamlReader.of(file, lines);
        int formatVersion = formatVersion(yaml, items);
        HudYamlReader hud = new HudYamlReader(yaml, name, formatVersion);
        List<HudOp> ops = new ArrayList<>();
        int first = formatVersion == HudProgram.FORMAT_GATES ? 0 : 1;
        for (int i = first; i < items.size(); i++) {
            hud.readItem(items.get(i), "[" + (i + 1) + "]", ops, topLevelKeys());
        }
        return new HudProgram(name, hud.ignoreAutoScale, ops, formatVersion);
    }

    /** The format a {@code SchemaVersion} first item names; the first format without one. */
    private static int formatVersion(YamlReader yaml, List<?> items) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Map<?, ?> item && item.containsKey(HudCommands.SCHEMA_VERSION)) {
                String path = "[" + (i + 1) + "]." + HudCommands.SCHEMA_VERSION;
                if (i > 0 || item.size() > 1) {
                    throw yaml.error(HudCommands.SCHEMA_VERSION + " is a list item of its own, the first one", path);
                }
                int version = yaml.value(Codecs.INT, item.get(HudCommands.SCHEMA_VERSION), path);
                if (version != HudProgram.FORMAT_GATES && version != HudProgram.FORMAT_NESTED) {
                    throw yaml.error("HUD files have the formats " + HudProgram.FORMAT_GATES + " and "
                            + HudProgram.FORMAT_NESTED + ", not " + version, path);
                }
                return version;
            }
        }
        return HudProgram.FORMAT_GATES;
    }

    private void readItem(Object item, String path, List<HudOp> ops, Set<String> known) {
        Map<String, Object> map = map(item, path);
        if (map.containsKey(IF)) {
            ops.add(readIf(map, path));
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (HudCommands.IGNORE_AUTO_SCALE.contains(key) && known.containsAll(HudCommands.IGNORE_AUTO_SCALE)) {
                this.ignoreAutoScale = this.reader.value(Codecs.BOOLEAN, entry.getValue(), path + "." + key);
            } else {
                readCommand(key, entry.getValue(), path, ops, known);
            }
        }
    }

    private HudOp.If readIf(Map<String, Object> map, String path) {
        String conditionPath = path + "." + IF;
        String condition = this.reader.value(HudCommands.CONDITION, map.get(IF), conditionPath);
        if (condition.isBlank()) {
            throw this.reader.error("the If needs a condition", conditionPath);
        }
        boolean nested = this.formatVersion == HudProgram.FORMAT_NESTED;
        List<String> blockKeys = nested ? List.of(IF, THEN, DO, ELSE) : List.of(IF, DO);
        for (String key : map.keySet()) {
            if (!blockKeys.contains(key)) {
                this.reader.unknownKey(path + "." + key, key, blockKeys);
            }
        }
        String bodyKey = nested && map.containsKey(THEN) ? THEN : DO;
        String bodyPath = path + "." + bodyKey;
        if (map.get(bodyKey) == null || list(map.get(bodyKey), bodyPath).isEmpty()) {
            this.reader.warn(path, "the If block has no commands");
        }
        List<HudOp> body = readBlock(map.get(bodyKey), bodyPath);
        List<HudOp> otherwise = nested ? readBlock(map.get(ELSE), path + "." + ELSE) : List.of();
        return new HudOp.If(line(conditionPath), condition, body, otherwise, 0);
    }

    /** The commands of a block; in format 2 they may be blocks themselves. */
    private List<HudOp> readBlock(Object block, String blockPath) {
        List<HudOp> ops = new ArrayList<>();
        List<?> items = block == null ? List.of() : list(block, blockPath);
        for (int i = 0; i < items.size(); i++) {
            String itemPath = blockPath + "[" + (i + 1) + "]";
            if (this.formatVersion == HudProgram.FORMAT_NESTED) {
                readItem(items.get(i), itemPath, ops, blockKeys());
            } else {
                for (Map.Entry<String, Object> entry : map(items.get(i), itemPath).entrySet()) {
                    readCommand(String.valueOf(entry.getKey()), entry.getValue(), itemPath, ops,
                            HudCommands.yamlKeys());
                }
            }
        }
        return ops;
    }

    /** Reads one command into {@code ops}; a key that names none is reported, an If inside Do included. */
    private void readCommand(String key, Object raw, String path, List<HudOp> ops, Set<String> known) {
        String keyPath = path + "." + key;
        Optional<HudCommand<?>> command = HudCommands.yaml(key);
        if (command.isEmpty()) {
            this.reader.unknownKey(keyPath, key, known);
            return;
        }
        Values entry = this.values.entry(ops.size());
        entry.set(HudCommands.LINE, line(keyPath));
        ops.add(command.get().takesNoArguments() ? command.get().arguments().build(entry)
                : this.reader.entry(command.get().arguments(), raw, entry, keyPath));
    }

    private int line(String keyPath) {
        return Math.max(0, this.reader.line(keyPath));
    }

    /** The keys a list item may start with: the commands, the settings and If. */
    private static Set<String> topLevelKeys() {
        Set<String> keys = new TreeSet<>(HudCommands.yamlKeys());
        keys.addAll(HudCommands.IGNORE_AUTO_SCALE);
        keys.add(IF);
        return keys;
    }

    /** The keys an item inside a format 2 block may start with: the commands and If. */
    private static Set<String> blockKeys() {
        Set<String> keys = new TreeSet<>(HudCommands.yamlKeys());
        keys.add(IF);
        return keys;
    }

    private Map<String, Object> map(Object raw, String path) {
        try {
            return Codecs.map(raw);
        } catch (ContentValueException e) {
            throw this.reader.error("each HUD command is a map such as '- DrawRectangle: ...', but " + e.getMessage(),
                    path);
        }
    }

    private List<?> list(Object raw, String path) {
        try {
            return Codecs.list(raw);
        } catch (ContentValueException e) {
            throw this.reader.error(e.getMessage(), path);
        }
    }
}
