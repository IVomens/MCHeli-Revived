package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.gametest.bench.ServerBenchmark;
import com.ivomens.mcheli.gametest.golden.GoldenTickHook;
import com.ivomens.mcheli.gametest.snapshot.EntitySeeding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The game events the tests listen to. Each loader's game test layer calls these from the same events
 * the common hooks use, after the common hooks of the same event, so the tests see the game in the
 * same state on every loader.
 */
public final class GameTestHooks {

    private GameTestHooks() {
    }

    /** Start of a server tick. */
    public static void onServerTickStart() {
        StressTests.onTickStart();
        ServerBenchmark.onTickStart();
    }

    /** End of a server tick. */
    public static void onServerTickEnd() {
        StressTests.onTickEnd();
        ServerBenchmark.onTickEnd();
    }

    /** Start of a level tick, on either side. */
    public static void onLevelTickStart(Level level) {
        GoldenTickHook.beforeLevelTick(level);
        ServerBenchmark.onLevelTickStart(level);
    }

    /** End of a level tick, on either side. */
    public static void onLevelTickEnd(Level level) {
        GoldenTickHook.afterLevelTick(level);
    }

    /** An entity is joining a level, on either side. */
    public static void onEntityJoinLevel(Entity entity, Level level) {
        EntitySeeding.onJoin(entity, level);
        ServerBenchmark.onEntityJoin(entity, level);
    }
}
