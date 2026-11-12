package com.ivomens.mcheli.bench.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StepWatchdogTest {

    private static final long MS = 1_000_000L;

    @Test
    void aStepAtAUsableRateRuns() {
        StepWatchdog watchdog = frames(100 * MS, 60);
        assertNull(watchdog.verdict());
    }

    @Test
    void aStepUnderFiveFpsIsAbortedAfterTwoSeconds() {
        StepWatchdog watchdog = frames(400 * MS, 8);
        assertEquals("too slow (2.5 FPS)", watchdog.verdict());
    }

    @Test
    void oneFrameOverTwoSecondsAbortsAtOnce() {
        StepWatchdog watchdog = new StepWatchdog();
        watchdog.start();
        long now = 0;
        for (int i = 0; i < 5; i++) {
            watchdog.frameStarted(now += 50 * MS);
        }
        watchdog.frameStarted(now + 2500 * MS);
        assertEquals("too slow (1.5 FPS, a frame took 2.5 s)", watchdog.verdict());
    }

    @Test
    void theFirstFramesOfAStepMayHitch() {
        StepWatchdog watchdog = new StepWatchdog();
        watchdog.start();
        long now = 0;
        // compiling shaders or uploading meshes when the renderer changes
        watchdog.frameStarted(now += 5000 * MS);
        watchdog.frameStarted(now += 3000 * MS);
        for (int i = 0; i < 60; i++) {
            watchdog.frameStarted(now += 20 * MS);
        }
        assertNull(watchdog.verdict());
    }

    @Test
    void aStoppedWatchdogJudgesNothing() {
        StepWatchdog watchdog = new StepWatchdog();
        watchdog.start();
        watchdog.stop();
        long now = 0;
        for (int i = 0; i < 10; i++) {
            watchdog.frameStarted(now += 3000 * MS);
        }
        assertNull(watchdog.verdict());
    }

    private static StepWatchdog frames(long interval, int count) {
        StepWatchdog watchdog = new StepWatchdog();
        watchdog.start();
        long now = 0;
        for (int i = 0; i < count; i++) {
            watchdog.frameStarted(now += interval);
        }
        return watchdog;
    }
}
