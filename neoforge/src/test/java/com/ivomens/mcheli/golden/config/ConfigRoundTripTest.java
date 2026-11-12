package com.ivomens.mcheli.golden.config;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.golden.Golden;
import com.ivomens.mcheli.golden.LogCapture;
import com.ivomens.mcheli.golden.MinecraftBootstrap;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Config golden (docs/ARCHITECTURE.md Part 2, §5.8): sample {@code mcheli.cfg} and
 * {@code mcheli-wingman.cfg} files (old versions, repeated keys, missing keys, values out of range or
 * not numbers) are read and written back the way the game does at start-up. The golden is the file
 * the game writes, plus what it logged; a typed config rewrite has to write the same file.
 */
class ConfigRoundTripTest {

    @TempDir
    Path workDir;

    @TestFactory
    List<DynamicTest> mcheliConfig() {
        return samples("mcheli").map(sample -> DynamicTest.dynamicTest(sample.getFileName().toString(),
                () -> Golden.check(goldenPath("mcheli", sample), header(sample), () -> roundTripMcheli(sample))))
                .toList();
    }

    @TestFactory
    List<DynamicTest> wingmanConfig() {
        return samples("wingman").map(sample -> DynamicTest.dynamicTest(sample.getFileName().toString(),
                () -> Golden.check(goldenPath("wingman", sample), header(sample), () -> roundTripWingman(sample))))
                .toList();
    }

    /** {@code McheliConfig.load} then {@code save}, as {@code CommonProxy.loadConfig} does. */
    private String roundTripMcheli(Path sample) {
        MinecraftBootstrap.ensure();
        Path file = copy(sample, this.workDir.resolve("mcheli").resolve("mcheli.cfg"));
        StringBuilder report = new StringBuilder();
        try (LogCapture log = LogCapture.start()) {
            try {
                McheliConfig.load(file);
                McheliConfig.save();
            } catch (RuntimeException e) {
                report.append("load threw ").append(e).append('\n');
            }
            appendLog(report, log, file);
        }
        // the running mod's version is written into the file; keep the golden independent of releases
        String written = read(file).replace("MOD_Version = " + Services.PLATFORM.getModVersion() + "\n",
                "MOD_Version = <mod version>\n");
        return report.append(written).toString();
    }

    /** {@code WingmanConfig.load(configDir)}, which reads the file (or the old TOML) and writes it back. */
    private String roundTripWingman(Path sample) {
        Path dir = this.workDir.resolve("wingman-" + sample.getFileName());
        boolean legacy = sample.getFileName().toString().endsWith(".toml");
        String target = legacy ? "mcheli-wingman.toml" : WingmanConfig.FILE_NAME;
        copy(sample, dir.resolve(target));
        StringBuilder report = new StringBuilder();
        try (LogCapture log = LogCapture.start()) {
            WingmanConfig.load(dir);
            appendLog(report, log, dir);
        }
        WingmanSettings settings = WingmanConfig.get();
        report.append("fields: maxWingmen=").append(settings.maxWingmen())
                .append(" formationSideDist=").append(settings.formationSideDist())
                .append(" uavControllerRange=").append(settings.uavControllerRange()).append('\n');
        return report.append(read(dir.resolve(WingmanConfig.FILE_NAME))).toString();
    }

    /** Log lines, with the temporary folder replaced so the text is the same on every machine. */
    private static void appendLog(StringBuilder report, LogCapture log, Path tempPath) {
        String folder = tempPath.getParent().toAbsolutePath().toString();
        log.lines().forEach(line -> report.append("log: ").append(line.replace(folder, "<config dir>")).append('\n'));
        report.append("-- written file --\n");
    }

    private static Stream<Path> samples(String kind) {
        Path resources = Path.of(System.getProperty("mcheli.testResources", "src/test/resources"));
        Path dir = resources.resolve("config").resolve(kind);
        try (Stream<Path> files = Files.list(dir)) {
            return files.sorted().toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path copy(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return to;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "<no file written>\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String goldenPath(String kind, Path sample) {
        return "config/" + kind + "/" + sample.getFileName() + ".written";
    }

    private static String header(Path sample) {
        return "{\"schema\":1,\"sample\":\"config/" + sample.getParent().getFileName() + "/" + sample.getFileName()
                + "\",\"commit\":\"" + Golden.commit() + "\"}";
    }
}
