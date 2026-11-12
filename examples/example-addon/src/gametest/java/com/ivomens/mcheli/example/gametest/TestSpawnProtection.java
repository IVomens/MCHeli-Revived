package com.ivomens.mcheli.example.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Spawn protection for the game tests. Vanilla only protects the spawn on a dedicated server (a game test
 * server answers "not protected" everywhere), so a mixin asks this class instead: the area a test switched on
 * is protected, for every player.
 */
public final class TestSpawnProtection {

    @Nullable
    private static volatile Area protectedArea;

    private TestSpawnProtection() {
    }

    /** Protects the area from now on, in the level only. */
    public static void protect(ServerLevel level, BoundingBox area) {
        protectedArea = new Area(level, area);
    }

    public static void clear() {
        protectedArea = null;
    }

    /** Whether a test protected the position. */
    public static boolean covers(ServerLevel level, BlockPos pos) {
        Area area = protectedArea;
        return area != null && area.level == level && area.box.isInside(pos);
    }

    private record Area(ServerLevel level, BoundingBox box) {
    }
}
