package com.ivomens.mcheli.gametest.snapshot;

import com.ivomens.mcheli.gametest.golden.GoldenMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Plain-text golden files for game tests that record something other than a vehicle trajectory:
 * weapon shots, lock-on, wingman flights. The first line is a {@code # } header for people; only
 * the lines after it are compared, exactly.
 * <p>
 * The mode is the one of the trajectory harness ({@link GoldenMode}): {@code -Pgolden=record} runs a
 * case twice, requires both runs to be identical and rewrites the file when its body changed.
 */
public final class TextGolden {

    private static final String HEADER_PREFIX = "# ";
    private static final int CONTEXT = 3;

    private TextGolden() {
    }

    /** How many times a case runs: twice when recording, to prove it is deterministic. */
    public static int repetitions() {
        return GoldenMode.CURRENT.records() ? 2 : 1;
    }

    /**
     * Checks the recordings of one case against {@code golden/<relativePath>}.
     *
     * @return a failure message, or null when the case passes
     */
    public static String check(String relativePath, String header, List<String> recordings) {
        Path file = GoldenMode.goldenDir().resolve(relativePath);
        String actual = recordings.getFirst();
        if (GoldenMode.CURRENT.records()) {
            for (int i = 1; i < recordings.size(); i++) {
                String difference = firstDifference(actual, recordings.get(i));
                if (difference != null) {
                    return relativePath + " is not deterministic: run " + (i + 1) + " differs from run 1 at "
                            + difference;
                }
            }
            return write(file, header, actual);
        }
        if (!Files.isRegularFile(file)) {
            return "no golden file at " + file + "; record it with ./gradlew runGameTestServer -Pgolden=record";
        }
        String difference = firstDifference(readBody(file), actual);
        return difference == null ? null : relativePath + " differs from the recording at " + difference;
    }

    private static String readBody(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            int end = text.indexOf('\n');
            return text.startsWith(HEADER_PREFIX) ? (end < 0 ? "" : text.substring(end + 1)) : text;
        } catch (IOException e) {
            return "<unreadable: " + e + ">";
        }
    }

    private static String write(Path file, String header, String body) {
        try {
            if (Files.isRegularFile(file) && readBody(file).equals(body)) {
                return null;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, HEADER_PREFIX + header + "\n" + body, StandardCharsets.UTF_8);
            return null;
        } catch (IOException e) {
            return "could not write " + file + ": " + e;
        }
    }

    /** Line number, a little context and both versions of the first differing line, or null. */
    static String firstDifference(String expected, String actual) {
        if (expected.equals(actual)) {
            return null;
        }
        List<String> want = expected.lines().toList();
        List<String> got = actual.lines().toList();
        int line = 0;
        while (line < want.size() && line < got.size() && want.get(line).equals(got.get(line))) {
            line++;
        }
        StringBuilder message = new StringBuilder("line ").append(line + 1).append(':');
        for (int i = Math.max(0, line - CONTEXT); i < line; i++) {
            message.append("\n    ").append(want.get(i));
        }
        message.append("\n  - ").append(line < want.size() ? want.get(line) : "<end of file>");
        message.append("\n  + ").append(line < got.size() ? got.get(line) : "<end of file>");
        return message.toString();
    }
}
