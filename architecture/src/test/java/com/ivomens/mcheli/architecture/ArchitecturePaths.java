package com.ivomens.mcheli.architecture;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Where the architecture tests find the production code.
 *
 * <p>Gradle passes these in as system properties (see the {@code test} block in build.gradle). The
 * fallbacks are the standard project layout, so a test started from an IDE in this module's folder
 * works too, as long as the loader modules' main classes have been compiled.
 */
final class ArchitecturePaths {

    private ArchitecturePaths() {
    }

    /**
     * The compiled main source sets of the loader modules, each holding the whole mod. Gametest and
     * test classes are deliberately left out.
     */
    static List<Path> mainClasses() {
        return paths(System.getProperty("architecture.mainClasses", String.join(File.pathSeparator,
                "../neoforge/build/classes/java/main", "../fabric/build/classes/java/main")));
    }

    /** The main source folders of every module. */
    static List<Path> mainSources() {
        return paths(System.getProperty("architecture.mainSources", String.join(File.pathSeparator,
                "../core/src/main/java", "../api/src/main/java", "../common/src/main/java", "../neoforge/src/main/java",
                "../fabric/src/main/java")));
    }

    private static List<Path> paths(String paths) {
        return Arrays.stream(paths.split(File.pathSeparator))
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .toList();
    }
}
