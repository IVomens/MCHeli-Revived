package com.ivomens.mcheli.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputCaptureTest {

    @Test
    void theAxisThatTravelsFurthestFromWhereItRestedIsTaken() {
        InputCapture capture = new InputCapture();
        // a trigger resting at -1 and a throttle lever at 0.8
        capture.start(new float[]{0.0F, 0.0F, -1.0F, 0.8F}, new boolean[0]);
        assertEquals(-1, capture.movedAxis(new float[]{0.3F, -0.2F, -1.0F, 0.8F}));
        assertEquals(1, capture.movedAxis(new float[]{0.3F, -0.9F, -0.6F, 0.8F}));
        assertEquals(3, capture.movedAxis(new float[]{0.0F, 0.0F, -1.0F, -0.8F}));
    }

    @Test
    void aButtonCountsWhenItGoesDownAfterTheStart() {
        InputCapture capture = new InputCapture();
        capture.start(new float[0], new boolean[]{true, false, false});
        assertEquals(-1, capture.pressedButton(new boolean[]{true, false, false}));
        assertEquals(2, capture.pressedButton(new boolean[]{true, false, true}));
        // the button held at the start counts once let go and pressed again
        assertEquals(-1, capture.pressedButton(new boolean[]{false, false, false}));
        assertEquals(0, capture.pressedButton(new boolean[]{true, false, false}));
    }
}
