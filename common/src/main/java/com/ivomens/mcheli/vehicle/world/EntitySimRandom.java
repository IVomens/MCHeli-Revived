package com.ivomens.mcheli.vehicle.world;

import com.ivomens.mcheli.core.sim.port.SimRandom;
import net.minecraft.util.RandomSource;

/** The entity's own random generator, so model draws interleave with everything else on the entity. */
public final class EntitySimRandom implements SimRandom {

    private final RandomSource random;

    public EntitySimRandom(RandomSource random) {
        this.random = random;
    }

    @Override
    public int nextInt(int bound) {
        return this.random.nextInt(bound);
    }

    @Override
    public double nextDouble() {
        return this.random.nextDouble();
    }
}
