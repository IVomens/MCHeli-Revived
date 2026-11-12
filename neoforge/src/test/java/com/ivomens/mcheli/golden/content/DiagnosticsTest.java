package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.golden.LogCapture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostics golden (docs/ARCHITECTURE.md Part 2, §5.3): what a pack author sees today for each
 * broken file in {@code src/test/resources/content/broken}. Many messages are poor (a raw
 * ClassCastException, or nothing at all); they are recorded as they are, so a rewrite can show
 * exactly which ones it improves.
 * <p>
 * Each file is parsed on its own, on top of the bundled content, and the golden lists whether the
 * game keeps the file, every line the mod logged while reading it, and for a kept file the values it
 * ended up with.
 */
class DiagnosticsTest {

    @TestFactory
    List<DynamicTest> brokenFiles() {
        ContentPipeline.load(ContentPipeline.bundledPack());
        Path pack = ContentPipeline.testPack("broken");
        List<DynamicTest> tests = new ArrayList<>();
        for (ContentType type : ContentType.values()) {
            for (String file : ContentPipeline.contentFiles(pack, type)) {
                tests.add(DynamicTest.dynamicTest(file, () -> Golden.check(goldenPath(file), header(file),
                        () -> diagnose(pack, file, type))));
            }
        }
        return tests;
    }

    private static String diagnose(Path pack, String file, ContentType type) {
        StringBuilder report = new StringBuilder();
        try (LogCapture log = LogCapture.start()) {
            LoadedContent<?, ?> content = ContentPipeline.parseOne(pack, file, type);
            report.append(content == null ? "result: skipped"
                    : "result: loaded as " + LegacyViews.legacyClassName(content)).append('\n');
            log.lines().forEach(line -> report.append(line).append('\n'));
            if (content != null) {
                // what a silently accepted value turned into (clamped, kept, defaulted)
                report.append("\nloaded values:\n").append(InfoDump.of(LegacyViews.of(content)));
            }
        }
        return report.toString();
    }

    private static String goldenPath(String file) {
        return "diagnostics/" + ContentPipeline.belowAssets(file) + ".log";
    }

    private static String header(String file) {
        return "{\"schema\":1,\"source\":\"" + file + "\",\"commit\":\"" + Golden.commit() + "\"}";
    }
}
