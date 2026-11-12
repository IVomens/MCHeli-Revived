package com.ivomens.mcheli.gametest.golden;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden trajectories: every case in {@link GoldenScripts} is driven exactly the way a client
 * would drive it and compared tick by tick with its committed recording (see docs/ARCHITECTURE.md,
 * section 4). Each case is its own batch, so the cases run one after another on the same course.
 */
public class GoldenTrajectoryTests {

    /** Ticks allowed on top of the script, per run. The course itself loads between two ticks (ForcedArea). */
    private static final int LOADING_TICKS = 600;

    public static List<TestFunction> goldenTrajectories() {
        List<TestFunction> tests = new ArrayList<>();
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return tests;
        }
        int repetitions = GoldenMode.CURRENT.records() ? 2 : 1;
        for (GoldenCase golden : GoldenScripts.all()) {
            int timeout = repetitions * (golden.script().ticks() + LOADING_TICKS);
            tests.add(new TestFunction(golden.testName(), golden.testName(), "mcheli:empty", timeout, 0, true,
                    helper -> run(helper, golden, repetitions)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, GoldenCase golden, int repetitions) {
        GoldenRun run = new GoldenRun(golden, helper, repetitions);
        GoldenTickHook.activate(run);
        helper.onEachTick(() -> {
            if (!run.isFinished()) {
                return;
            }
            GoldenTickHook.deactivate(run);
            String problem = run.failure() != null ? run.failure() : GoldenVerdict.check(golden, run.recordings());
            if (problem != null) {
                throw new GameTestAssertException(golden.relativePath() + ": " + problem);
            }
            helper.succeed();
        });
    }
}
