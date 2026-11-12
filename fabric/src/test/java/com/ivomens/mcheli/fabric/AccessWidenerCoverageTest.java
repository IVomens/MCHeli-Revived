package com.ivomens.mcheli.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Common code compiles against vanilla opened up by common's access transformer, and runs on Fabric
 * with the access widener instead. Every transformer entry needs a widener entry, or the Fabric jar
 * fails at runtime with an access error the compiler never saw.
 */
class AccessWidenerCoverageTest {

    @Test
    void wideningCoversEveryTransformerEntry() throws IOException {
        Set<String> widened = new HashSet<>();
        for (String line : lines("mcheli.accessWidener")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 3 || parts[0].startsWith("accessWidener")) {
                continue;
            }
            // access, kind, owner [, name, descriptor]
            String kind = parts[1];
            String key = switch (kind) {
                case "class" -> "class " + parts[2];
                case "method" -> "method " + parts[2] + " " + parts[3] + parts[4];
                case "field" -> "field " + parts[2] + " " + parts[3];
                default -> throw new IllegalStateException("Unknown access widener entry: " + line);
            };
            widened.add(parts[0] + " " + key);
        }

        List<String> missing = new ArrayList<>();
        for (String line : lines("mcheli.accessTransformer")) {
            String[] parts = line.split("\\s+");
            String owner = parts[1].replace('.', '/');
            String key;
            if (parts.length == 2) {
                key = "class " + owner;
            } else if (parts[2].contains("(")) {
                key = "method " + owner + " " + parts[2];
            } else {
                key = "field " + owner + " " + parts[2];
            }
            if (!widened.contains("accessible " + key)) {
                missing.add(line + " (needs accessible " + key + ")");
            }
            if (parts[0].endsWith("-f") && !widened.contains("mutable " + key)) {
                missing.add(line + " (needs mutable " + key + ")");
            }
        }
        assertTrue(missing.isEmpty(), "Access transformer entries missing from mcheli.accesswidener:\n"
                + String.join("\n", missing));
    }

    /** The non-blank, non-comment lines of the file a system property names. */
    private static List<String> lines(String property) throws IOException {
        String path = System.getProperty(property);
        assertTrue(path != null, property + " is not set; run the test through Gradle");
        return Files.readAllLines(Path.of(path)).stream()
                .map(line -> line.replaceFirst("#.*", "").strip())
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
