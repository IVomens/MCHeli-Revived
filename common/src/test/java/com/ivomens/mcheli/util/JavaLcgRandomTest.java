package com.ivomens.mcheli.util;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The combat rolls keep their recorded values only if this generator matches {@link Random} draw for draw. */
class JavaLcgRandomTest {

    @Test
    void drawsTheSameNumbersAsJavaUtilRandom() {
        for (long seed : new long[]{0L, 1L, -7L, 20261106L, Long.MAX_VALUE}) {
            Random expected = new Random(seed);
            JavaLcgRandom actual = new JavaLcgRandom(seed);
            for (int i = 0; i < 2000; i++) {
                assertEquals(expected.nextInt(), actual.nextInt());
                assertEquals(expected.nextInt(360), actual.nextInt(360));
                assertEquals(expected.nextInt(64), actual.nextInt(64));
                assertEquals(expected.nextFloat(), actual.nextFloat());
                assertEquals(expected.nextDouble(), actual.nextDouble());
                assertEquals(expected.nextBoolean(), actual.nextBoolean());
                assertEquals(expected.nextLong(), actual.nextLong());
                assertEquals(expected.nextGaussian(), actual.nextGaussian());
            }
        }
    }

    @Test
    void reseedingStartsTheSequenceOver() {
        JavaLcgRandom random = new JavaLcgRandom(5L);
        double first = random.nextDouble();
        random.setSeed(5L);
        assertEquals(first, random.nextDouble());
    }
}
