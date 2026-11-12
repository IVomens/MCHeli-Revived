package com.ivomens.mcheli.content.emit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivomens.mcheli.content.registry.ContentType;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every bundled YAML file is valid under the JSON Schema of its type. The schemas must never be stricter than
 * the game: a file the game reads without a message passes. (The bundled files load without messages; the
 * content dump and diagnostics goldens in {@code :neoforge} check that side.)
 * <p>
 * Files are parsed with SnakeYAML, as the game parses them. The CI job and the addon action use check-jsonschema,
 * which parses YAML 1.2; docs/ADDON_AUTHORING.md lists the few spellings where the two differ.
 */
class BundledContentSchemaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static Path bundledPack() {
        return Path.of(System.getProperty("mcheli.mainResources", "src/main/resources"));
    }

    /** The schema of a type as the committed file has it, without descriptions. */
    static JsonSchema schema(ContentType type) throws IOException {
        JsonNode document = JSON.readTree(JsonSchemaEmitter.write(JsonSchemas.document(type, declaration -> null)));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document);
    }

    /** The problems of one YAML file under its type's schema, one per line. */
    static List<String> problems(JsonSchema schema, Path file) throws IOException {
        Object document = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
        Set<ValidationMessage> messages = schema.validate(JSON.valueToTree(document));
        return messages.stream().map(ValidationMessage::getMessage).sorted().toList();
    }

    @TestFactory
    List<DynamicTest> bundledFilesAreValid() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (ContentType type : ContentType.values()) {
            JsonSchema schema = schema(type);
            for (Path file : yamlFiles(bundledPack().resolve("assets/mcheli").resolve(type.dirName))) {
                tests.add(DynamicTest.dynamicTest(type.dirName + "/" + file.getFileName(), () -> {
                    List<String> problems = problems(schema, file);
                    assertTrue(problems.isEmpty(), file + " is not valid under " + JsonSchemas.fileName(type)
                            + ":\n  " + String.join("\n  ", problems));
                }));
            }
        }
        assertTrue(tests.size() > 300, "expected the bundled content, found " + tests.size() + " files");
        return tests;
    }

    /** What the schema reports, so the bundled files passing means something. */
    @Test
    void reportsWhatTheGameReports() throws IOException {
        JsonSchema weapon = schema(ContentType.WEAPON);
        assertEquals(List.of(), problems(weapon, """
                SchemaVersion: 1
                Type: Rocket
                SwitchCooldown: 3       # no longer supported: deprecated, still valid
                Ammo: { MagSize: 30 }
                Camera: { Sight: lasersight }   # the game ignores sight names it does not know
                """));
        List<String> problems = problems(weapon, """
                Ammo: { MagSize: 0, Magazine: 3 }
                Explosion: { Flaming: 1 }
                """);
        assertEquals(3, problems.size(), String.join("\n", problems));

        JsonSchema helicopter = schema(ContentType.HELICOPTER);
        assertEquals(List.of(), problems(helicopter, "Seats:\n- Pos: [0, 1, 0]\n"));
        assertEquals(1, problems(helicopter, "Seats:\n- Pos: [0, 1]\n").size());
        assertTrue(!problems(helicopter, "Seats:\n- Camera: {}\n").isEmpty(), "a seat needs a position");
    }

    private static List<String> problems(JsonSchema schema, String yaml) {
        return schema.validate(JSON.valueToTree(new Yaml().load(yaml))).stream().map(ValidationMessage::getMessage)
                .sorted().toList();
    }

    static List<Path> yamlFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(file -> file.toString().endsWith(".yml")).sorted().collect(Collectors.toList());
        }
    }
}
