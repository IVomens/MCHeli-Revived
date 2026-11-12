package com.ivomens.mcheli.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickFilterTest {

    @Test
    void withoutATimeConstantTheStickIsPassedOnExactly() {
        StickFilter filter = new StickFilter();
        assertArrayEquals(new float[]{12.345F, -0.0F}, filter.smooth(12.345F, -0.0F, 0.016F, 0.0F));
        assertArrayEquals(new float[]{-3.0F, 7.0F}, filter.smooth(-3.0F, 7.0F, 0.016F, 0.0F));
    }

    @Test
    void smoothingFollowsTheStickAndDoesNotDependOnTheFrameRate() {
        StickFilter sixty = new StickFilter();
        StickFilter thirty = new StickFilter();
        sixty.smooth(0.0F, 0.0F, 0.0F, 0.1F);
        thirty.smooth(0.0F, 0.0F, 0.0F, 0.1F);
        float[] a = null;
        float[] b = null;
        for (int i = 0; i < 6; i++) {
            a = sixty.smooth(10.0F, -10.0F, 1.0F / 60.0F, 0.1F);
        }
        for (int i = 0; i < 3; i++) {
            b = thirty.smooth(10.0F, -10.0F, 1.0F / 30.0F, 0.1F);
        }
        // after 0.1 s (one time constant) about 63 % of the way
        assertEquals(10.0F * (1.0F - (float) Math.exp(-1.0)), a[0], 1.0e-3F);
        assertEquals(a[0], b[0], 1.0e-3F);
        assertEquals(-a[0], a[1], 1.0e-5F);
    }

    @Test
    void recentringKeepsAllWithoutARateAndLessWithOne() {
        assertEquals(1.0, StickFilter.recentre(0.0, 0.05));
        assertEquals(Math.exp(-0.1), StickFilter.recentre(2.0, 0.05), 1.0e-12);
        assertTrue(StickFilter.recentre(5.0, 1.0) < 0.01);
    }
}
