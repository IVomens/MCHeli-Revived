package com.ivomens.mcheli.gametest.golden;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Whether the harness checks the vehicles against the committed files or writes new ones.
 * <p>
 * {@code ./gradlew runGameTestServer} compares. {@code ./gradlew runGameTestServer -Pgolden=record}
 * runs every script twice, fails if the two runs differ, and rewrites the files whose data changed.
 * build.gradle passes the source folders, so recording writes straight into {@code src/gametest/resources}.
 */
public enum GoldenMode {
    COMPARE,
    RECORD;

    public static final GoldenMode CURRENT =
            "record".equals(System.getProperty("mcheli.golden", "compare").toLowerCase(Locale.ROOT)) ? RECORD : COMPARE;

    /** Where the trajectory files live: {@code src/gametest/resources/golden}. */
    public static Path goldenDir() {
        return sourceDir("mcheli.golden.dir", "src/gametest/resources/golden");
    }

    /** Where the save fixtures live: {@code src/gametest/resources/fixtures/nbt}. */
    public static Path fixtureDir() {
        return sourceDir("mcheli.fixtures.dir", "src/gametest/resources/fixtures/nbt");
    }

    /** The commit the recording was made at, for the file header only; never compared. */
    public static String commit() {
        return System.getProperty("mcheli.golden.commit", "unknown");
    }

    public boolean records() {
        return this == RECORD;
    }

    private static Path sourceDir(String property, String fallback) {
        String dir = System.getProperty(property);
        // the run directory is <project>/run, so the fallback resolves from its parent
        return dir != null ? Path.of(dir) : Path.of("..").resolve(fallback).normalize();
    }
}
