package com.ivomens.mcheli.gametest.golden;

import net.minecraft.world.level.Level;

/**
 * Hands the overworld's tick to the golden run in progress. Only one run is active at a time,
 * since every golden test is its own batch. Nothing happens here outside the golden tests. The loader's
 * game test glue calls the two tick methods around every level tick (see {@code GameTestHooks}).
 */
public final class GoldenTickHook {

    private static GoldenRun active;

    private GoldenTickHook() {
    }

    static void activate(GoldenRun run) {
        if (active != null) {
            active.abort();
        }
        active = run;
        run.start();
    }

    static void deactivate(GoldenRun run) {
        if (active == run) {
            active = null;
        }
    }

    public static void beforeLevelTick(Level level) {
        if (active != null && level.dimension() == Level.OVERWORLD && !level.isClientSide()) {
            active.beforeLevelTick();
        }
    }

    public static void afterLevelTick(Level level) {
        if (active != null && level.dimension() == Level.OVERWORLD && !level.isClientSide()) {
            active.afterLevelTick();
        }
    }
}
