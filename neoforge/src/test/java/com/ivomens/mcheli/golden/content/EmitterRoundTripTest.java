package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.golden.Golden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emitter round trip (docs/ARCHITECTURE.md Part 2, §5.2): every content file is parsed, written
 * back as YAML with its {@link ContentFormat}'s emitter (the converter players use for old packs), and the YAML is
 * parsed again. The second dump should equal the first.
 * <p>
 * The YAML emitted from the TXT corpus is a golden file of its own. Where today's emitter loses or
 * changes a value, the differing dump lines are recorded in {@code emitter/*-differences.txt}, so a
 * rewrite has to keep them or knowingly fix them.
 */
class EmitterRoundTripTest {

    /** Dump lines that name the source file and format, which a conversion changes on purpose. */
    private static final List<String> SOURCE_FIELDS = List.of("filePath = ", "parserIdentifier = ", "fileName = ");
    /** HUD elements remember their TXT line for error messages; YAML has none. */
    private static final String LINE_FIELD = ".fileLine = ";

    @TempDir
    Path workDir;

    @Test
    void txtCorpusSurvivesConversionToYaml() throws IOException {
        Map<String, LoadedContent<?, ?>> original = TxtFixtures.load();
        Map<String, String> emitted = emitAll(original);
        emitted.forEach((file, yaml) -> Golden.check(goldenPath(file), header(file), () -> yaml));

        Path converted = writePack(this.workDir.resolve("txt"), emitted);
        Map<String, LoadedContent<?, ?>> reparsed = ContentPipeline.load(ContentPipeline.bundledPack(), converted);
        checkDifferences("emitter/txt-differences.txt", original, reparsed);
    }

    @Test
    void bundledYamlSurvivesReEmission() throws IOException {
        Map<String, LoadedContent<?, ?>> original = ContentPipeline.load(ContentPipeline.bundledPack());
        Path converted = writePack(this.workDir.resolve("bundled"), emitAll(original));
        Map<String, LoadedContent<?, ?>> reparsed = ContentPipeline.load(converted);
        checkDifferences("emitter/bundled-differences.txt", original, reparsed);
    }

    /** Records which dump lines change through the round trip, per file; ideally the file is empty. */
    private static void checkDifferences(String goldenPath, Map<String, LoadedContent<?, ?>> original,
                                         Map<String, LoadedContent<?, ?>> reparsed) {
        Golden.check(goldenPath, "{\"schema\":1,\"what\":\"dump lines changed by parse -> YamlEmitter -> parse\","
                + "\"commit\":\"" + Golden.commit() + "\"}", () -> {
            StringBuilder report = new StringBuilder();
            original.forEach((file, info) -> {
                String yamlFile = yamlName(file);
                LoadedContent<?, ?> again = reparsed.get(yamlFile);
                List<String> lines = again == null ? List.of("  <the emitted YAML did not parse>")
                        : differingLines(InfoDump.of(LegacyViews.of(info)), InfoDump.of(LegacyViews.of(again)));
                if (!lines.isEmpty()) {
                    report.append(file).append('\n');
                    lines.forEach(line -> report.append(line).append('\n'));
                }
            });
            return report.toString();
        });
    }

    private static List<String> differingLines(String before, String after) {
        Map<String, String> want = byPath(before);
        Map<String, String> got = byPath(after);
        Map<String, String> paths = new TreeMap<>();
        want.keySet().forEach(path -> paths.put(path, path));
        got.keySet().forEach(path -> paths.put(path, path));
        List<String> lines = new ArrayList<>();
        for (String path : paths.keySet()) {
            String a = want.getOrDefault(path, "<absent>");
            String b = got.getOrDefault(path, "<absent>");
            if (!a.equals(b)) {
                lines.add("  " + path + ": " + a + " -> " + b);
            }
        }
        return lines;
    }

    private static Map<String, String> byPath(String dump) {
        Map<String, String> values = new TreeMap<>();
        dump.lines()
                .filter(line -> SOURCE_FIELDS.stream().noneMatch(line::startsWith) && !line.contains(LINE_FIELD))
                .forEach(line -> {
                    int separator = line.indexOf(" = ");
                    values.put(line.substring(0, separator), line.substring(separator + 3));
                });
        return values;
    }

    private static Map<String, String> emitAll(Map<String, LoadedContent<?, ?>> infos) {
        Map<String, String> emitted = new TreeMap<>();
        infos.forEach((file, info) -> emitted.put(yamlName(file), emit(info)));
        return emitted;
    }

    private static String emit(LoadedContent<?, ?> loaded) {
        try {
            return ContentFormat.of(loaded.entry().type()).emit(loaded.entry());
        } catch (RuntimeException e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return "# emission failed: " + e.getMessage() + " (" + cause + ")\n";
        }
    }

    private static Path writePack(Path pack, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path out = pack.resolve(file.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, file.getValue());
        }
        return pack;
    }

    private static String yamlName(String file) {
        return file.substring(0, file.lastIndexOf('.')) + ".yml";
    }

    private static String goldenPath(String yamlFile) {
        return "emitter/" + ContentPipeline.belowAssets(yamlFile);
    }

    private static String header(String file) {
        return "{\"schema\":1,\"emitted from\":\"" + file.substring(0, file.lastIndexOf('.')) + ".txt\",\"commit\":\""
                + Golden.commit() + "\"}";
    }
}
