package com.ivomens.mcheli.gametest.wingman;

import com.ivomens.mcheli.gametest.golden.GoldenMode;
import com.ivomens.mcheli.gametest.golden.PinnedSettings;
import com.ivomens.mcheli.gametest.golden.SeededRandoms;
import com.ivomens.mcheli.gametest.golden.TestTerrain;
import com.ivomens.mcheli.gametest.snapshot.EntitySeeding;
import com.ivomens.mcheli.gametest.snapshot.QuietWorld;
import com.ivomens.mcheli.gametest.snapshot.TextGolden;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wingman scenarios (docs/ARCHITECTURE.md Part 2, §5.10): followers converging on their formation
 * slots, and an autonomous runway takeoff into the climb. Each runs on the golden test course,
 * rebuilt 60 blocks lower so the autopilot's absolute cruise altitude (Y 80) lies above the runway,
 * and is compared with {@code golden/wingman/<scenario>.txt}.
 * <p>
 * Not covered yet: the landing circuit back to PARKED and the VTOL helipad cycle (the circuit needs
 * a larger loaded area than the course has), and the restart case, which WingmanServerStopGameTests
 * already checks.
 */
public class WingmanScenarioTests {

    static final long SEED = 20261106L;
    /** The golden course, moved down so Y 80 is 40 blocks above its runway. */
    private static final BlockPos COURSE = TestTerrain.POSITIVE.below(60);
    /** Ticks allowed on top of the scenario, per run. The course itself loads between two ticks (ForcedArea). */
    private static final int LOADING_TICKS = 600;

    public static List<TestFunction> scenarios() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        List<Supplier<WingmanScenario>> scenarios = List.of(FormationScenario::new, RunwayTakeoffScenario::new);
        List<TestFunction> tests = new ArrayList<>();
        int repetitions = TextGolden.repetitions();
        for (Supplier<WingmanScenario> scenario : scenarios) {
            String name = "wingman_" + scenario.get().name();
            int timeout = repetitions * (scenario.get().ticks() + LOADING_TICKS);
            tests.add(new TestFunction(name, name, "mcheli:empty", timeout, 0, true,
                    helper -> run(helper, scenario, repetitions)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, Supplier<WingmanScenario> scenarios, int repetitions) {
        ServerLevel level = helper.getLevel();
        TestTerrain terrain = new TestTerrain(level, COURSE);
        PinnedSettings settings = PinnedSettings.pin();
        Runnable randomTicks = QuietWorld.pin(level);
        WingmanSettings wingmanSettings = WingmanConfig.get();
        WingmanConfig.set(wingmanSettings.withFormation(20.0, 0.0, 30.0));
        terrain.forceChunks(true);

        List<String> recordings = new ArrayList<>();
        Runnable restore = () -> {
            terrain.forceChunks(false);
            settings.restore();
            randomTicks.run();
            WingmanConfig.set(wingmanSettings);
        };
        ScenarioRun run = new ScenarioRun(level, terrain, scenarios, recordings, settings);
        helper.onEachTick(() -> {
            try {
                if (!run.tick()) {
                    return;
                }
                if (recordings.size() < repetitions) {
                    run.restart();
                    return;
                }
            } catch (RuntimeException e) {
                run.abort();
                restore.run();
                throw new GameTestAssertException(run.name() + ": " + e);
            }
            restore.run();
            String golden = "wingman/" + run.name() + ".txt";
            String problem = TextGolden.check(golden, header(run.name()), recordings);
            if (problem != null) {
                throw new GameTestAssertException(problem);
            }
            helper.succeed();
        });
    }

    private static String header(String name) {
        return "{\"schema\":1,\"scenario\":\"" + name + "\",\"seed\":" + SEED + ",\"course\":" + "{\"origin\":["
                + COURSE.getX() + "," + COURSE.getY() + "," + COURSE.getZ() + "]},\"formation\":\"side 20, rear 30,"
                + " alt 0\",\"pinned\":" + PinnedSettings.describe() + ",\"commit\":\"" + GoldenMode.commit() + "\"}";
    }

    /** Drives one scenario: wait for the course, build it, fly, record. */
    private static final class ScenarioRun {

        private final ServerLevel level;
        private final TestTerrain terrain;
        private final Supplier<WingmanScenario> factory;
        private final List<String> recordings;
        private final PinnedSettings settings;
        private WingmanScenario scenario;
        private List<String> lines;
        private int tick = -1;

        ScenarioRun(ServerLevel level, TestTerrain terrain, Supplier<WingmanScenario> factory,
                    List<String> recordings, PinnedSettings settings) {
            this.level = level;
            this.terrain = terrain;
            this.factory = factory;
            this.recordings = recordings;
            this.settings = settings;
            this.scenario = factory.get();
        }

        String name() {
            return this.scenario.name();
        }

        /** Advances one tick; true when the current recording is complete. */
        boolean tick() {
            if (this.tick < 0) {
                this.terrain.awaitChunks();
                this.terrain.clearEntities();
                this.terrain.build();
                SeededRandoms.seedGlobal(this.level, SEED);
                EntitySeeding.start(SEED);
                this.scenario.setUp(this.level, this.terrain);
                this.terrain.aircraft().forEach(this.settings::pinVehicle);
                this.lines = new ArrayList<>();
                this.tick = 0;
                return false;
            }
            this.scenario.afterTick(++this.tick, this.lines);
            if (this.tick < this.scenario.ticks()) {
                return false;
            }
            this.scenario.tearDown(this.level);
            EntitySeeding.stop();
            this.terrain.clearEntities();
            StringBuilder text = new StringBuilder();
            this.lines.forEach(line -> text.append(line).append('\n'));
            this.recordings.add(text.toString());
            return true;
        }

        void restart() {
            this.scenario = this.factory.get();
            this.tick = -1;
        }

        void abort() {
            EntitySeeding.stop();
            if (this.tick >= 0) {
                this.scenario.tearDown(this.level);
            }
            this.terrain.clearEntities();
        }
    }
}
