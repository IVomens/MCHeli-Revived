package com.ivomens.mcheli.content.registry;

import com.ivomens.mcheli.McheliMod;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.pack.AddonPack;
import com.ivomens.mcheli.content.pack.PackOrder;
import com.ivomens.mcheli.content.report.ContentReport;
import com.ivomens.mcheli.content.report.Diagnostic.Severity;
import com.ivomens.mcheli.content.report.FileOverride;
import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.golden.MinecraftBootstrap;
import com.ivomens.mcheli.golden.content.ContentPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the community pack fixtures in {@code src/test/resources/content/community} through the game's loader,
 * in pack order, and checks which files win and what the content report says (docs/ADDON_AUTHORING.md):
 * <ul>
 *     <li>{@code default}: a stand-in for the bundled content, with its domain {@code @builtin};</li>
 *     <li>{@code Desert_Storm_Pack}: an old TXT pack saved on Windows (no pack.mcmeta, CRLF, a BOM, tabs, keys
 *     in any case, settings the game no longer reads, a typo, an upper-case file name);</li>
 *     <li>{@code zz_ru_patch}: a YAML pack that patches both (comments, a BOM, a newer SchemaVersion, two files
 *     with one name, a tab where YAML wants spaces).</li>
 * </ul>
 * The report is a golden file: {@code golden/report/community.txt}.
 */
class CommunityPacksTest {

    private static Path packsDir() {
        return ContentPipeline.testPack("community");
    }

    /** The packs in the folder, in the order the game loads them. */
    static List<AddonPack> packs(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            return children.map(Path::toFile).filter(File::isDirectory).sorted(PackOrder.LOAD_ORDER)
                    .map(AddonPack::create).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ContentReport load() {
        MinecraftBootstrap.ensure();
        McheliMod.registerContentTypes();
        return ContentRegistries.load(() -> packs(packsDir()));
    }

    @Test
    void reportMatchesTheRecording() {
        Golden.check("report/community.txt", "{\"schema\":1,\"source\":\"content/community\",\"commit\":\""
                + Golden.commit() + "\"}", () -> load().text());
    }

    @Test
    void theBundledPackLoadsFirstAndLaterPacksWin() {
        ContentReport report = load();
        assertEquals(List.of("@builtin", "desert_storm_pack", "ru_patch"),
                report.packs().stream().map(ContentReport.Pack::domain).toList());

        assertWinner(ContentType.WEAPON, "m2_cal50", "desert_storm_pack", "assets/mcheli/weapons/m2_cal50.txt");
        assertWinner(ContentType.WEAPON, "gau8", "ru_patch", "assets/mcheli/weapons/gau8.yml");
        assertWinner(ContentType.WEAPON, "hydra70", "ru_patch", "assets/mcheli/weapons/hydra70.yml");
        assertWinner(ContentType.HELICOPTER, "scout", "ru_patch", "assets/mcheli/helicopters/scout.yml");
        assertWinner(ContentType.THROWABLE, "smoke_green", "@builtin", "assets/mcheli/throwable/smoke_green.yml");

        assertEquals(List.of(
                        "weapons 'm2_cal50': 'assets/mcheli/weapons/m2_cal50.txt' in pack 'desert_storm_pack' replaces "
                                + "'assets/mcheli/weapons/m2_cal50.yml' in pack '@builtin'",
                        "weapons 'gau8': 'assets/mcheli/weapons/gau8.yml' in pack 'ru_patch' replaces "
                                + "'assets/mcheli/weapons/gau8.txt' in pack 'desert_storm_pack'",
                        "weapons 'hydra70': 'assets/mcheli/weapons/hydra70.txt' in pack 'ru_patch' replaces "
                                + "'assets/mcheli/weapons/hydra70.yml' in pack '@builtin'",
                        "weapons 'hydra70': pack 'ru_patch' has two files with this name; "
                                + "'assets/mcheli/weapons/hydra70.yml' is used and 'assets/mcheli/weapons/hydra70.txt' "
                                + "is ignored",
                        "helicopters 'scout': 'assets/mcheli/helicopters/scout.yml' in pack 'ru_patch' replaces "
                                + "'assets/mcheli/helicopters/scout.yml' in pack '@builtin'"),
                report.overrides().stream().map(FileOverride::describe).toList());
    }

    @Test
    void replacingAnotherAddonIsAWarningButReplacingBundledContentIsNot() {
        ContentReport report = load();
        List<String> warnings = report.diagnostics().stream().filter(d -> d.severity() == Severity.WARNING)
                .map(d -> d.message()).toList();
        assertTrue(warnings.stream().anyMatch(m -> m.startsWith("weapons 'gau8': ")), String.join("\n", warnings));
        assertTrue(warnings.stream().anyMatch(m -> m.startsWith("weapons 'hydra70': pack 'ru_patch' has two")));
        assertFalse(warnings.stream().anyMatch(m -> m.startsWith("weapons 'm2_cal50': ")));
    }

    @Test
    void windowsFilesReadLikeAnyOther() {
        load();
        // the BOM is not part of the first key, and CRLF line ends and tabs around '=' are fine
        assertEquals("GAU-8 (RU)", ((WeaponDefinition) ContentRegistries.registry(ContentType.WEAPON).entry("gau8")
                .orElseThrow().definition()).displayName());
        ContentReport report = load();
        assertFalse(report.text().contains("\uFEFF"), "a byte order mark reached a message");
        assertTrue(report.diagnostics().stream().noneMatch(d -> d.message().contains("unknown key 'displayname'")));
    }

    @Test
    void theReportIsWrittenAsText(@TempDir Path dir) throws IOException {
        ContentReport report = load();
        Path file = dir.resolve("logs/mcheli-content.log");
        report.write(file, "header");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("header\nContent load\n"), text);
        assertTrue(report.summary("logs/mcheli-content.log").matches("Content load: \\d+ of \\d+ files loaded from 3 "
                + "packs, \\d+ errors, \\d+ warnings, 5 files replaced\\. Full report: logs/mcheli-content.log"),
                report.summary("logs/mcheli-content.log"));
    }

    @Test
    void packOrderIgnoresCaseAndPutsTheBundledPackFirst() {
        List<String> names = Stream.of("zz_ru_patch", "Desert_Storm_Pack", "alpha.zip", "default", "Beta", "beta")
                .map(File::new).sorted(PackOrder.LOAD_ORDER).map(File::getName).toList();
        assertEquals(Arrays.asList("default", "alpha.zip", "Beta", "beta", "Desert_Storm_Pack", "zz_ru_patch"), names);
    }

    private static void assertWinner(ContentType type, String name, String pack, String file) {
        var entry = ContentRegistries.registry(type).entry(name).orElseThrow(() -> new AssertionError(name));
        assertEquals(pack, entry.location().getAddonDomain(), name);
        assertEquals(file, entry.filePath(), name);
    }
}
