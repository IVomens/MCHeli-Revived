package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.gametest.golden.AttitudeDriver.ClientFrames;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * One golden recording: a script driven on one vehicle definition.
 *
 * @param type       the folder under {@code golden/}: helicopter, plane, vtol, ship, tank, tank_car,
 *                   vehicle or target_drone
 * @param definition the definition name, e.g. {@code ah-64}
 * @param info       looks the definition up when the test runs (content loads after test discovery)
 * @param frames     the client the harness plays; the 60 FPS cases get their own files
 */
record GoldenCase(String type, String definition, Supplier<VehicleSpec> info, Script script, ClientFrames frames) {

    /** The fixed seed of every generator a run touches. */
    static final long SEED = 20261106L;

    GoldenCase(String type, String definition, Supplier<VehicleSpec> info, Script script) {
        this(type, definition, info, script, ClientFrames.TWENTY_FPS);
    }

    /** The same case flown by a 60 FPS client with fixed attitude substeps. */
    GoldenCase atSixtyFps() {
        return new GoldenCase(this.type, this.definition, this.info, this.script, ClientFrames.SIXTY_FPS);
    }

    String testName() {
        return ("golden_" + this.type + "_" + this.definition + "_" + recordingName())
                .replaceAll("[^a-z0-9_]", "_");
    }

    String relativePath() {
        return GoldenFile.relativePath(this.type, this.definition, recordingName());
    }

    private String recordingName() {
        return this.script.name() + this.frames.suffix();
    }

    /** A short hash of the definition file, so a failing comparison can say the definition changed. */
    static String definitionHash(VehicleSpec info) {
        try (InputStream in = open(VehicleTypes.entry(info).map(ContentEntry::filePath).orElse(""))) {
            if (in == null) {
                return "unknown";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(in.readAllBytes())).substring(0, 16);
        } catch (IOException | NoSuchAlgorithmException | InvalidPathException e) {
            return "unknown";
        }
    }

    private static InputStream open(String filePath) throws IOException {
        if (filePath == null) {
            return null;
        }
        Path path = Path.of(filePath);
        if (Files.isRegularFile(path)) {
            return Files.newInputStream(path);
        }
        String resource = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        return GoldenCase.class.getClassLoader().getResourceAsStream(resource);
    }
}
