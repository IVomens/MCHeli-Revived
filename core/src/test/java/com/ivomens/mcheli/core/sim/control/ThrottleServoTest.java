package com.ivomens.mcheli.core.sim.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrottleServoTest {

    @Test
    void pressesTowardsTheTargetAndRestsInsideTheDeadBand() {
        assertEquals(ThrottleCommand.UP, ThrottleServo.toward(0.5, 0.6, 0.01));
        assertEquals(ThrottleCommand.DOWN, ThrottleServo.toward(0.7, 0.6, 0.01));
        assertEquals(ThrottleCommand.NONE, ThrottleServo.toward(0.605, 0.6, 0.01));
        assertEquals(ThrottleCommand.NONE, ThrottleServo.toward(0.595, 0.6, 0.01));
    }
}
