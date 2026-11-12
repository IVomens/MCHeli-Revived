package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.bench.BenchmarkScene;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs the in-game benchmark the way a player does, active with -Dmcheli.clientBenchmarkCheck=quick
 * (or {@code full}): once in the world it types {@code /mcheli benchmark client [quick]}, waits for the
 * result files, then checks that the benchmark put everything back (client options, mcheli.cfg settings,
 * the player's place, no benchmark vehicles left) and logs {@code [benchcheck] PASS} or the problems
 * before closing the game. The loader's game test layer calls the hooks through {@link ClientSceneHooks}.
 */
public final class ClientBenchmarkCheck {

    private static final String MODE = System.getProperty("mcheli.clientBenchmarkCheck", "");
    private static final int START_TICK = 100;
    private static final int SETTLE_AFTER_FILES = 60;
    /** In the quick run: the first orbit while it measures, and a later step. */
    private static final int ORBIT_SCREENSHOT_AFTER = 90;
    private static final int SCREENSHOT_AFTER = 440;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;

    private static int ticks;
    private static int doneAt = -1;
    private static Before before;
    private static List<Path> existing = List.of();

    private record Before(int renderDistance, int framerateLimit, boolean vsync, boolean pauseOnLostFocus,
                         CameraType camera, McheliSettings config, Vec3 position) {
    }

    private ClientBenchmarkCheck() {
    }

    static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (MODE.isEmpty() || mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        if (ticks == START_TICK - 40 && mc.player.isPassenger()) {
            // the devscene save may have the player in a vehicle; the benchmark asks to get out first
            mc.player.connection.sendCommand("ride @s dismount");
        }
        if (ticks == START_TICK) {
            existing = results(mc);
            before = new Before(mc.options.renderDistance().get(), mc.options.framerateLimit().get(),
                    mc.options.enableVsync().get(), mc.options.pauseOnLostFocus, mc.options.getCameraType(),
                    McheliConfig.get(), mc.player.position());
            mc.player.connection.sendCommand("mcheli benchmark client" + (MODE.equals("full") ? "" : " quick"));
            ModLogger.info("[benchcheck] started the benchmark ({})", MODE);
        }
        if (ticks > START_TICK && doneAt < 0 && results(mc).size() >= existing.size() + 2) {
            doneAt = ticks;
        }
        if (ticks == START_TICK + ORBIT_SCREENSHOT_AFTER) {
            Screenshot.grab(mc.gameDirectory, "benchcheck-orbit.png", mc.getMainRenderTarget(), c -> {
            });
        }
        if (ticks == START_TICK + SCREENSHOT_AFTER) {
            Screenshot.grab(mc.gameDirectory, "benchcheck.png", mc.getMainRenderTarget(), c -> {
            });
        }
        if (doneAt > 0 && ticks == doneAt + SETTLE_AFTER_FILES) {
            verify(mc);
            mc.stop();
        } else if (ticks > TIMEOUT_TICKS) {
            ModLogger.error("[benchcheck] FAIL: no results after {} ticks", ticks);
            mc.stop();
        }
    }

    private static void verify(Minecraft mc) {
        List<String> problems = new ArrayList<>();
        check(problems, "render distance", before.renderDistance(), mc.options.renderDistance().get());
        check(problems, "frame rate limit", before.framerateLimit(), mc.options.framerateLimit().get());
        check(problems, "vsync", before.vsync(), mc.options.enableVsync().get());
        check(problems, "pause on lost focus", before.pauseOnLostFocus(), mc.options.pauseOnLostFocus);
        check(problems, "camera", before.camera(), mc.options.getCameraType());
        check(problems, "mcheli.cfg settings", before.config(), McheliConfig.get());
        if (mc.player.getVehicle() != null) {
            problems.add("the player still rides " + mc.player.getVehicle());
        }
        // back where it stood; it may have dropped a few blocks since, if it stood on something the
        // scene pushed away (the devscene player starts on top of a helicopter)
        Vec3 now = mc.player.position();
        double drop = before.position().y - now.y;
        if (now.subtract(before.position()).horizontalDistance() > 2 || drop < -2 || drop > 6) {
            problems.add("the player is at " + mc.player.position() + ", not back at " + before.position());
        }
        int left = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getTags().contains(BenchmarkScene.TAG)) {
                left++;
            }
        }
        if (left > 0) {
            problems.add(left + " benchmark vehicles are left in the world");
        }
        List<Path> files = results(mc);
        files.removeAll(existing);
        ModLogger.info("[benchcheck] new result files: {}", files);
        for (Path file : files) {
            if (file.toString().endsWith(".txt")) {
                try {
                    ModLogger.info("[benchcheck] summary:\n{}", Files.readString(file));
                } catch (IOException e) {
                    problems.add("cannot read " + file);
                }
            }
        }
        if (problems.isEmpty()) {
            ModLogger.info("[benchcheck] PASS");
        } else {
            ModLogger.error("[benchcheck] FAIL: {}", problems);
        }
    }

    private static void check(List<String> problems, String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            problems.add(what + " is " + actual + ", was " + expected);
        }
    }

    private static List<Path> results(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("mcheli").resolve("benchmarks");
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return new ArrayList<>(files.filter(f -> f.getFileName().toString().startsWith("mcheli-benchmark-")).toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
