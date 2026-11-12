package com.ivomens.mcheli.golden;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Golden text files for the unit tests: what today's code produces, committed under
 * {@code src/test/resources/golden}, so a rewrite can prove it still produces the same.
 * <p>
 * {@code ./gradlew test} compares. {@code ./gradlew test -Pgolden=record} produces every file twice,
 * fails if the two runs differ, and rewrites the files whose body changed. The first line of a file
 * is a {@code # } header for people (what was recorded and how); only the lines after it are compared.
 */
public final class Golden {

    private static final boolean RECORD =
            "record".equals(System.getProperty("mcheli.golden", "compare").toLowerCase(Locale.ROOT));
    private static final String HEADER_PREFIX = "# ";
    /** Lines of context shown around the first difference. */
    private static final int CONTEXT = 3;

    private Golden() {
    }

    public static boolean recording() {
        return RECORD;
    }

    /** {@code src/test/resources/golden}, passed in by build.gradle so recording writes into the sources. */
    public static Path dir() {
        String dir = System.getProperty("mcheli.golden.testDir");
        return dir != null ? Path.of(dir) : Path.of("src/test/resources/golden");
    }

    /**
     * Checks one golden file.
     *
     * @param relativePath the file below {@link #dir()}
     * @param header       one line describing the recording; never compared
     * @param body         produces the content; called twice when recording to prove it is deterministic
     */
    public static void check(String relativePath, String header, Supplier<String> body) {
        Path file = dir().resolve(relativePath);
        String actual = body.get();
        if (RECORD) {
            String again = body.get();
            String difference = firstDifference(actual, again);
            if (difference != null) {
                throw new AssertionError(relativePath + " is not deterministic; the second run differs at "
                        + difference);
            }
            write(file, header, actual);
            return;
        }
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("no golden file at " + file + "; record it with ./gradlew test -Pgolden=record");
        }
        String difference = firstDifference(readBody(file), actual);
        if (difference != null) {
            throw new AssertionError(relativePath + " differs from the recording at " + difference
                    + "\nIf the change is intended, re-record with ./gradlew test -Pgolden=record and review the"
                    + " diff.");
        }
    }

    /** The body of a golden file, without its header line. */
    public static String readBody(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.startsWith(HEADER_PREFIX)) {
                return text;
            }
            int end = text.indexOf('\n');
            return end < 0 ? "" : text.substring(end + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Line number and both lines of the first difference, or null when equal. */
    static String firstDifference(String expected, String actual) {
        if (expected.equals(actual)) {
            return null;
        }
        List<String> want = lines(expected);
        List<String> got = lines(actual);
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
        message.append("\n  (expected ").append(want.size()).append(" lines, got ").append(got.size()).append(')');
        return message.toString();
    }

    private static List<String> lines(String text) {
        return new ArrayList<>(text.lines().toList());
    }

    /** Rewrites the file when its body changed; an unchanged body keeps the old header (and its commit). */
    private static void write(Path file, String header, String body) {
        try {
            if (Files.isRegularFile(file) && readBody(file).equals(body)) {
                return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, HEADER_PREFIX + header + "\n" + body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The commit the recording was made at, for headers only. */
    public static String commit() {
        return System.getProperty("mcheli.golden.commit", "unknown");
    }
}
