package com.ivomens.mcheli.gametest.snapshot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * Turns off random block ticks while a snapshot records. Random ticks pick their blocks with a
 * generator of the level that cannot be seeded from outside, and the grass of the superflat world
 * near the test players then draws from the level's random at unpredictable moments. That broke the
 * otherwise exact replay of explosions (block drops land where the level's random puts them).
 */
public final class QuietWorld {

    private QuietWorld() {
    }

    /** Sets {@code randomTickSpeed} to 0; run the returned action to put the old value back. */
    public static Runnable pin(ServerLevel level) {
        GameRules.IntegerValue rule = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        int previous = rule.get();
        rule.set(0, level.getServer());
        return () -> rule.set(previous, level.getServer());
    }
}
