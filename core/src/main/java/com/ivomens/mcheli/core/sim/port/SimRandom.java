package com.ivomens.mcheli.core.sim.port;

/**
 * The random numbers a model draws. In game it is the vehicle's own seeded generator, so draws must
 * keep their order: every call here advances that generator for everything else on the entity too.
 */
public interface SimRandom {

    /** Uniform in {@code [0, bound)}. */
    int nextInt(int bound);

    /** Uniform in {@code [0, 1)}. */
    double nextDouble();
}
