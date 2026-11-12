package com.ivomens.mcheli.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottleAxisTest {

    @Test
    void aLeverPushedForwardIsFullThrottle() {
        assertEquals(1.0, ThrottleAxis.leverPosition(-1.0F));
        assertEquals(0.5, ThrottleAxis.leverPosition(0.0F));
        assertEquals(0.0, ThrottleAxis.leverPosition(1.0F));
        assertEquals(0.0, ThrottleAxis.leverPosition(5.0F));
    }

    @Test
    void anAbsoluteAxisFollowsTheLever() {
        ThrottleAxis axis = new ThrottleAxis();
        assertEquals(0.75, axis.update(ThrottleAxis.Mode.ABSOLUTE, -0.5F, 0.1, 0.05, 1.0));
        assertEquals(0.25, axis.update(ThrottleAxis.Mode.ABSOLUTE, 0.5F, 0.9, 0.05, 1.0));
    }

    @Test
    void anIncrementalAxisStartsFromTheThrottleAndIntegratesItsDeflection() {
        ThrottleAxis axis = new ThrottleAxis();
        assertTrue(Double.isNaN(axis.update(ThrottleAxis.Mode.INCREMENTAL, 0.0F, 0.4, 0.05, 0.5)),
                "untouched, it leaves the throttle alone");
        // full forward for one second at 0.5 per second
        double target = Double.NaN;
        for (int i = 0; i < 20; i++) {
            target = axis.update(ThrottleAxis.Mode.INCREMENTAL, -1.0F, 0.4, 0.05, 0.5);
        }
        assertEquals(0.9, target, 1.0e-9);
        assertEquals(0.9, axis.update(ThrottleAxis.Mode.INCREMENTAL, 0.0F, 0.2, 0.05, 0.5), 1.0e-9,
                "centred, the target stays");
        for (int i = 0; i < 100; i++) {
            target = axis.update(ThrottleAxis.Mode.INCREMENTAL, 1.0F, 0.2, 0.05, 0.5);
        }
        assertEquals(0.0, target, "the target stops at idle");
    }

    @Test
    void releaseStartsTheNextTargetFromTheThrottle() {
        ThrottleAxis axis = new ThrottleAxis();
        axis.update(ThrottleAxis.Mode.INCREMENTAL, -1.0F, 0.4, 0.05, 0.5);
        axis.release();
        assertEquals(0.7 + 0.025, axis.update(ThrottleAxis.Mode.INCREMENTAL, -1.0F, 0.7, 0.05, 0.5), 1.0e-9);
    }
}
