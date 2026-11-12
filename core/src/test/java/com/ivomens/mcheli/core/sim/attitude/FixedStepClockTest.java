package com.ivomens.mcheli.core.sim.attitude;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedStepClockTest {

    private static int stepsOver(float frameSeconds, int frames) {
        FixedStepClock clock = FixedStepClock.START;
        int steps = 0;
        for (int i = 0; i < frames; i++) {
            FixedStepClock.Frame frame = clock.advance(frameSeconds);
            steps += frame.steps();
            clock = frame.clock();
        }
        return steps;
    }

    @Test
    void sixtyStepsASecondWhateverTheFrameRate() {
        assertEquals(60, stepsOver(1.0F / 144.0F, 144), 1);
        assertEquals(60, stepsOver(1.0F / 30.0F, 30), 1);
        assertEquals(60, stepsOver(1.0F / 60.0F, 60), 1);
    }

    @Test
    void theModelRunsJustAheadAndTheFrameIsDrawnBetweenSteps() {
        FixedStepClock.Frame frame = FixedStepClock.START.advance(FixedStepClock.STEP_SECONDS * 1.5F);
        assertEquals(2, frame.steps());
        assertEquals(0.5F, frame.fraction(), 1.0e-3F);
    }

    @Test
    void aFrameOnAStepShowsThatStep() {
        FixedStepClock clock = FixedStepClock.START;
        for (int i = 0; i < 100; i++) {
            FixedStepClock.Frame frame = clock.advance(FixedStepClock.STEP_SECONDS);
            assertEquals(1, frame.steps());
            assertEquals(1.0F, frame.fraction());
            clock = frame.clock();
        }
    }

    @Test
    void aHitchOnlyStepsATenthOfASecond() {
        FixedStepClock.Frame frame = FixedStepClock.START.advance(2.0F);
        assertTrue(frame.steps() <= 7, "steps " + frame.steps());
    }
}
