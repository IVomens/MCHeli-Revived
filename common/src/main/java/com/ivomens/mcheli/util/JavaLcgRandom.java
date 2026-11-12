package com.ivomens.mcheli.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;

/**
 * A {@link RandomSource} that draws exactly the numbers {@code java.util.Random} draws from the same
 * seed.
 * <p>
 * The game's own {@code LegacyRandomSource} shares the generator but not every conversion: its
 * {@code nextDouble} scales by a float constant that is not 2<sup>-53</sup>. Weapon spread and the other
 * combat rolls were tuned, and recorded in the firing snapshots, with {@code java.util.Random}, so they
 * use this class to keep every value while taking their generator by injection.
 */
public final class JavaLcgRandom implements RandomSource {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;
    private static final double DOUBLE_UNIT = 0x1.0p-53;
    private static final float FLOAT_UNIT = 0x1.0p-24f;

    private long seed;
    private double nextGaussian;
    private boolean haveNextGaussian;

    public JavaLcgRandom(long seed) {
        this.setSeed(seed);
    }

    /** A generator with a fresh unique seed, like {@code new Random()}. */
    public static JavaLcgRandom unseeded() {
        return new JavaLcgRandom(RandomSupport.generateUniqueSeed());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
        this.haveNextGaussian = false;
    }

    private int next(int bits) {
        this.seed = (this.seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (this.seed >>> (48 - bits));
    }

    @Override
    public int nextInt() {
        return this.next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) this.next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = this.next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    @Override
    public long nextLong() {
        return ((long) this.next(32) << 32) + this.next(32);
    }

    @Override
    public boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    public float nextFloat() {
        return this.next(24) * FLOAT_UNIT;
    }

    @Override
    public double nextDouble() {
        return (((long) this.next(26) << 27) + this.next(27)) * DOUBLE_UNIT;
    }

    @Override
    public double nextGaussian() {
        if (this.haveNextGaussian) {
            this.haveNextGaussian = false;
            return this.nextGaussian;
        }
        double v1;
        double v2;
        double s;
        do {
            v1 = 2 * this.nextDouble() - 1;
            v2 = 2 * this.nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
        this.nextGaussian = v2 * multiplier;
        this.haveNextGaussian = true;
        return v1 * multiplier;
    }

    @Override
    public RandomSource fork() {
        return new JavaLcgRandom(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }
}
