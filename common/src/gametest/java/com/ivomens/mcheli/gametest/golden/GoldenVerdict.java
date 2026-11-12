package com.ivomens.mcheli.gametest.golden;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Turns finished recordings into a test result: in record mode the two runs must agree and the
 * file is rewritten if its data changed; in compare mode the run must match the committed file.
 */
final class GoldenVerdict {

    private GoldenVerdict() {
    }

    /** A failure message, or null when the case passes. */
    static String check(GoldenCase golden, List<GoldenFile> recordings) {
        Path file = GoldenMode.goldenDir().resolve(golden.relativePath());
        GoldenFile actual = recordings.getFirst();
        if (GoldenMode.CURRENT.records()) {
            return record(file, recordings);
        }
        if (!Files.isRegularFile(file)) {
            return "no golden file at " + file + "; record it with ./gradlew runGameTestServer -Pgolden=record";
        }
        GoldenFile expected = read(file);
        Optional<String> difference = TrajectoryComparator.firstDifference(expected, actual);
        if (difference.isEmpty()) {
            return null;
        }
        String message = "differs from the recording at " + difference.get();
        String expectedHash = GoldenHeader.definitionHash(expected.header());
        String actualHash = GoldenHeader.definitionHash(actual.header());
        if (!expectedHash.equals(actualHash)) {
            message += " (the definition file changed: " + expectedHash + " -> " + actualHash + ")";
        }
        return message;
    }

    private static String record(Path file, List<GoldenFile> recordings) {
        for (int i = 1; i < recordings.size(); i++) {
            Optional<String> difference =
                    TrajectoryComparator.firstDifference(recordings.getFirst(), recordings.get(i));
            if (difference.isPresent()) {
                return "not deterministic: run " + (i + 1) + " differs from run 1 at " + difference.get();
            }
        }
        GoldenFile recorded = recordings.getFirst();
        try {
            // keep the old file (and its commit) when nothing but the header would change
            if (!Files.isRegularFile(file) || !read(file).sameData(recorded)) {
                recorded.write(file);
            }
        } catch (IOException e) {
            return "could not write " + file + ": " + e;
        }
        return null;
    }

    private static GoldenFile read(Path file) {
        try {
            return GoldenFile.read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
