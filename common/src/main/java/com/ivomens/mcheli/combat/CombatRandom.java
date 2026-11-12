package com.ivomens.mcheli.combat;

import com.ivomens.mcheli.util.JavaLcgRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The random generators of a level's weapons, one stream per kind of roll so that one kind cannot
 * shift the numbers of another. Each level owns its own (see {@code MixinLevel}), so replays seed them
 * through {@link #of} and nothing about them is global.
 * <p>
 * Projectiles and countermeasure entities roll with their own entity generator; these streams are for
 * the weapons and explosions that have no entity of their own.
 */
public final class CombatRandom {

    /** Implemented on {@link Level} by a mixin. */
    public interface Holder {
        CombatRandom mcheli$combatRandom();
    }

    private final RandomSource aim = JavaLcgRandom.unseeded();
    private final RandomSource barrels = JavaLcgRandom.unseeded();
    private final RandomSource blasts = JavaLcgRandom.unseeded();
    private final RandomSource rays = JavaLcgRandom.unseeded();

    public static CombatRandom of(Level level) {
        return ((Holder) level).mcheli$combatRandom();
    }

    /** Shot spread, the pitch of fire sounds and the approach of air strikes. */
    public RandomSource aim() {
        return this.aim;
    }

    /** How fast rotary barrels spin up. */
    public RandomSource barrels() {
        return this.barrels;
    }

    /** Which blocks an explosion sets on fire, and the splash of underwater ones. */
    public RandomSource blasts() {
        return this.blasts;
    }

    /**
     * How far each ray of an explosion reaches into the blocks. Not the level's own generator, which chunk
     * ticking draws from a varying number of times, so which blocks a blast breaks replays exactly.
     */
    public RandomSource rays() {
        return this.rays;
    }
}
