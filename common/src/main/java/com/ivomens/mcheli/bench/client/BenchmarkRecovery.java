package com.ivomens.mcheli.bench.client;

import com.ivomens.mcheli.config.FlightSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.RenderSettings;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Puts the player's settings back after a benchmark run that never finished: the game was killed or
 * crashed mid-run. The benchmark changes options and mcheli.cfg settings only in memory, but anything
 * that saves them meanwhile (an options screen, a config reload) writes the benchmark's values to disk.
 * So a run writes the player's values to {@code mcheli/benchmarks/benchmark-in-progress.properties}
 * when it starts and deletes the file when it ends; a file found when the client starts means a run
 * was cut short, and its values are put back and saved. The leftover benchmark vehicles are removed by
 * the server side ({@code BenchmarkScene}). Client thread only.
 */
final class BenchmarkRecovery {

    private static final String FILE = "benchmark-in-progress.properties";

    private BenchmarkRecovery() {
    }

    /** Notes the player's settings before the benchmark changes them. */
    static void begin(Path gameDir, ClientSettings saved) {
        Properties values = new Properties();
        values.setProperty("renderDistance", String.valueOf(saved.renderDistance()));
        values.setProperty("framerateLimit", String.valueOf(saved.framerateLimit()));
        values.setProperty("vsync", String.valueOf(saved.vsync()));
        values.setProperty("pauseOnLostFocus", String.valueOf(saved.pauseOnLostFocus()));
        values.setProperty("vehicleRenderer", saved.config().render().vehicleRenderer());
        values.setProperty("fixedStepAttitude", String.valueOf(saved.config().flight().fixedStepAttitude()));
        Path file = file(gameDir);
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file)) {
                values.store(out, "Settings to put back if this benchmark run never finishes");
            }
        } catch (IOException e) {
            ModLogger.warn("Could not note the settings to put back after the benchmark: {}", e.toString());
        }
    }

    /** The run ended and put everything back itself. */
    static void end(Path gameDir) {
        try {
            Files.deleteIfExists(file(gameDir));
        } catch (IOException e) {
            ModLogger.warn("Could not delete {}: {}", file(gameDir), e.toString());
        }
    }

    /** Puts back the settings of a run that was cut short, if there was one. */
    static void recover(Minecraft mc) {
        Path file = file(mc.gameDirectory.toPath());
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties values = new Properties();
        try (Reader in = Files.newBufferedReader(file)) {
            values.load(in);
            restoreOptions(mc.options, values);
            restoreConfig(values);
            Files.delete(file);
            ModLogger.info("A client benchmark run did not finish last time; put back the settings it changed.");
        } catch (IOException | IllegalArgumentException e) {
            ModLogger.warn("Could not put back the settings of an unfinished benchmark run from {}: {}", file,
                    e.toString());
        }
    }

    private static void restoreOptions(Options options, Properties values) {
        options.renderDistance().set(Integer.parseInt(values.getProperty("renderDistance")));
        options.framerateLimit().set(Integer.parseInt(values.getProperty("framerateLimit")));
        options.enableVsync().set(Boolean.parseBoolean(values.getProperty("vsync")));
        options.pauseOnLostFocus = Boolean.parseBoolean(values.getProperty("pauseOnLostFocus"));
        options.save();
    }

    private static void restoreConfig(Properties values) {
        String renderer = values.getProperty("vehicleRenderer");
        String fixedStep = values.getProperty("fixedStepAttitude");
        if (renderer == null || fixedStep == null) {
            throw new IllegalArgumentException("incomplete file");
        }
        McheliSettings before = McheliConfig.get();
        McheliSettings after = McheliConfig.update(settings -> settings
                .with(RenderSettings.VEHICLE_RENDERER, renderer)
                .with(FlightSettings.FIXED_STEP_ATTITUDE, Boolean.parseBoolean(fixedStep)));
        if (!after.equals(before)) {
            McheliConfig.save();
        }
    }

    private static Path file(Path gameDir) {
        return gameDir.resolve("mcheli").resolve("benchmarks").resolve(FILE);
    }
}
