package com.ivomens.mcheli.gametest.snapshot;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Seeds the random generator of every entity that joins the world while a snapshot runs: rounds,
 * bomblets, flares, cartridges. Each entity otherwise starts from a fresh random seed, which would
 * make spread, bomblet scatter and fuze rolls differ from run to run. The n-th entity of a case
 * always gets the same seed, so the game code needs no hook for it. The loader's game test glue calls
 * {@link #onJoin} as entities join a level (see {@code GameTestHooks}).
 */
public final class EntitySeeding {

    private static boolean active;
    private static long seed;
    private static int joined;

    private EntitySeeding() {
    }

    public static void start(long caseSeed) {
        active = true;
        seed = caseSeed;
        joined = 0;
    }

    public static void stop() {
        active = false;
    }

    public static void onJoin(Entity entity, Level level) {
        if (active && !level.isClientSide()) {
            entity.getRandom().setSeed(seed + 31L * ++joined);
        }
    }
}
