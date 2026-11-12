package com.ivomens.mcheli.content.emit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Writes the JSON Schema files of {@link JsonSchemas} to the repository's {@code schemas/} folder:
 * {@code ./gradlew generateSchemas}. {@link JsonSchemaFilesTest} fails the build while the committed files differ
 * from what the declarations give.
 */
public final class JsonSchemaFiles {

    private JsonSchemaFiles() {
    }

    /** The files by name, with descriptions from the sources below the {@code sources} folders. */
    static Map<String, String> generate(List<Path> sources) {
        return JsonSchemas.files(SchemaDocs.read(sources));
    }

    /** The {@code *.schema.json} files in {@code dir}, sorted. */
    static List<Path> existing(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(JsonSchemas.EXTENSION)).sorted()
                    .toList();
        }
    }

    /**
     * @param args the folder to write to ({@code schemas/}) and the {@code src/main/java} folders of
     *             {@code :common} and {@code :core}
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        Map<String, String> files = generate(List.of(Path.of(args[1]), Path.of(args[2])));
        Files.createDirectories(out);
        for (Path stale : existing(out)) {
            if (!files.containsKey(stale.getFileName().toString())) {
                Files.delete(stale);
            }
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(out.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("Wrote " + files.size() + " JSON Schema files to " + out.toAbsolutePath());
        System.exit(0);
    }
}
