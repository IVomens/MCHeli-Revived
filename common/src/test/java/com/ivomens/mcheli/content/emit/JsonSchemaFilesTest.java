package com.ivomens.mcheli.content.emit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The committed {@code schemas/*.schema.json} files are what the schema declarations give today. They are never
 * edited by hand: after changing a schema, run {@code ./gradlew generateSchemas} and commit the result.
 */
class JsonSchemaFilesTest {

    private static final String HOW_TO_FIX = "\nRun ./gradlew generateSchemas and commit the schemas/ folder.";

    static Path schemasDir() {
        return Path.of(System.getProperty("mcheli.schemasDir", "../schemas"));
    }

    static List<Path> sourcesDirs() {
        return List.of(Path.of(System.getProperty("mcheli.commonSources", "src/main/java")),
                Path.of(System.getProperty("mcheli.coreSources", "../core/src/main/java")));
    }

    @Test
    void committedSchemasAreUpToDate() throws IOException {
        Map<String, String> generated = JsonSchemaFiles.generate(sourcesDirs());
        TreeSet<String> committed = new TreeSet<>();
        for (Path file : JsonSchemaFiles.existing(schemasDir())) {
            committed.add(file.getFileName().toString());
        }
        assertEquals(new TreeSet<>(generated.keySet()), committed, "the schema files in " + schemasDir()
                + HOW_TO_FIX);
        for (Map.Entry<String, String> file : generated.entrySet()) {
            String onDisk = Files.readString(schemasDir().resolve(file.getKey()), StandardCharsets.UTF_8);
            if (!onDisk.equals(file.getValue())) {
                fail(file.getKey() + " is out of date: " + firstDifference(onDisk, file.getValue()) + HOW_TO_FIX);
            }
        }
    }

    private static String firstDifference(String committed, String generated) {
        String[] a = committed.split("\n", -1);
        String[] b = generated.split("\n", -1);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String left = i < a.length ? a[i] : "<end of file>";
            String right = i < b.length ? b[i] : "<end of file>";
            if (!left.equals(right)) {
                return "line " + (i + 1) + "\n  committed: " + left.strip() + "\n  generated: " + right.strip();
            }
        }
        return "line endings differ";
    }
}
