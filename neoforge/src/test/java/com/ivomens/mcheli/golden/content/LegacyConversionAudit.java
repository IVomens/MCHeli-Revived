package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.source.ContentFormat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares the bundled YAML with the original McHeli TXT files they were converted from, value by
 * value. Runs only when {@code MCHELI_LEGACY_PACK} names a pack folder holding the TXT files under
 * {@code assets/mcheli/<type>/}; it writes every difference to {@code MCHELI_LEGACY_REPORT} (or
 * {@code build/legacy-conversion.txt}). With {@code MCHELI_LEGACY_EMIT} set to a folder, it also writes both sides
 * through the YAML emitter to {@code txt/} and {@code yml/} below it, so a plain {@code diff -r} shows each
 * difference as the YAML it takes.
 */
class LegacyConversionAudit {

    @Test
    void compareWithLegacyTxt() throws IOException {
        String legacy = System.getenv("MCHELI_LEGACY_PACK");
        Assumptions.assumeTrue(legacy != null && !legacy.isEmpty(), "MCHELI_LEGACY_PACK not set");
        Path legacyPack = Path.of(legacy);
        Map<Path, Map<String, LoadedContent<?, ?>>> parsed = ContentPipeline.loadEach(ContentPipeline.bundledPack(),
                legacyPack);
        Map<String, LoadedContent<?, ?>> bundled = byStem(parsed.get(ContentPipeline.bundledPack()));
        Map<String, LoadedContent<?, ?>> txt = byStem(parsed.get(legacyPack));
        StringBuilder report = new StringBuilder();
        for (Map.Entry<String, LoadedContent<?, ?>> entry : txt.entrySet()) {
            LoadedContent<?, ?> yaml = bundled.get(entry.getKey());
            if (yaml == null) {
                report.append(entry.getKey()).append(": no bundled YAML\n");
                continue;
            }
            for (String line : diff(dump(yaml), dump(entry.getValue()))) {
                report.append(entry.getKey()).append(": ").append(line).append('\n');
            }
        }
        String out = System.getenv().getOrDefault("MCHELI_LEGACY_REPORT", "build/legacy-conversion.txt");
        Files.writeString(Path.of(out), report.toString());
        String emit = System.getenv("MCHELI_LEGACY_EMIT");
        if (emit != null && !emit.isEmpty()) {
            for (Map.Entry<String, LoadedContent<?, ?>> entry : txt.entrySet()) {
                LoadedContent<?, ?> yaml = bundled.get(entry.getKey());
                if (yaml != null) {
                    write(Path.of(emit, "txt", entry.getKey() + ".yml"), entry.getValue());
                    write(Path.of(emit, "yml", entry.getKey() + ".yml"), yaml);
                }
            }
        }
    }

    private static void write(Path file, LoadedContent<?, ?> loaded) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, ContentFormat.of(loaded.entry().type()).emit(loaded.entry()));
    }

    private static Map<String, LoadedContent<?, ?>> byStem(Map<String, LoadedContent<?, ?>> files) {
        Map<String, LoadedContent<?, ?>> stems = new TreeMap<>();
        files.forEach((file, loaded) -> stems.put(ContentPipeline.belowAssets(file).replaceAll("\\.(yml|yaml|txt)$", "")
                .toLowerCase(Locale.ROOT), loaded));
        return stems;
    }

    private static Map<String, String> dump(LoadedContent<?, ?> loaded) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (String line : InfoDump.of(LegacyViews.of(loaded)).split("\n")) {
            int eq = line.indexOf(" = ");
            if (eq > 0) {
                lines.put(line.substring(0, eq), line.substring(eq + 3));
            }
        }
        return lines;
    }

    /** {@code path: yaml -> txt} for every path whose value differs. */
    private static List<String> diff(Map<String, String> yaml, Map<String, String> txt) {
        List<String> lines = new ArrayList<>();
        Map<String, String> all = new TreeMap<>(yaml);
        txt.forEach(all::putIfAbsent);
        for (String key : all.keySet()) {
            String a = yaml.get(key);
            String b = txt.get(key);
            if (a == null ? b != null : !a.equals(b)) {
                lines.add(key + ": " + a + " -> " + b);
            }
        }
        return lines;
    }
}
