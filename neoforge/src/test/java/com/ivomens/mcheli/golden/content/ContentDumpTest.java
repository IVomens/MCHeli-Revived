package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.golden.Golden;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Content canonical dump (docs/ARCHITECTURE.md Part 2, §5.1): every value run-time code reads of every
 * file parsed from the bundled YAML and from the legacy TXT fixture corpus, one golden file per content file
 * under {@code golden/content/}. The dumps keep the field names of the info classes they were recorded
 * from; {@link LegacyViews} maps today's records onto them.
 */
class ContentDumpTest {

    /** Bundled files the parsers must accept; a file that stops parsing is a regression. */
    private static final int BUNDLED_FILES = 372;

    @TestFactory
    List<DynamicTest> bundledYaml() {
        Map<String, LoadedContent<?, ?>> parsed = ContentPipeline.load(ContentPipeline.bundledPack());
        assertEquals(BUNDLED_FILES, parsed.size(), "bundled content files that parsed");
        return dumpTests("bundled", parsed);
    }

    @TestFactory
    List<DynamicTest> legacyTxtFixtures() {
        Map<String, LoadedContent<?, ?>> parsed = TxtFixtures.load();
        return dumpTests("txt", parsed);
    }

    static List<DynamicTest> dumpTests(String pack, Map<String, LoadedContent<?, ?>> parsed) {
        return parsed.entrySet().stream()
                .map(entry -> DynamicTest.dynamicTest(entry.getKey(),
                        () -> checkDump(pack, entry.getKey(), entry.getValue())))
                .toList();
    }

    private static void checkDump(String pack, String file, LoadedContent<?, ?> loaded) {
        Golden.check(goldenPath(pack, file), header(file, loaded), () -> InfoDump.of(LegacyViews.of(loaded)));
    }

    /** {@code content/<pack>/<type folder>/<file>.dump}. */
    static String goldenPath(String pack, String file) {
        return "content/" + pack + "/" + ContentPipeline.belowAssets(file) + ".dump";
    }

    private static String header(String file, LoadedContent<?, ?> loaded) {
        return "{\"schema\":1,\"source\":\"" + file + "\",\"parser\":\"" + loaded.entry().format()
                + "\",\"class\":\"" + LegacyViews.legacyClassName(loaded) + "\",\"commit\":\"" + Golden.commit()
                + "\"}";
    }
}
