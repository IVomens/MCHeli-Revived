package com.ivomens.mcheli.bench.client;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Gives up on a benchmark step the machine cannot draw: one that averages under {@link #MIN_FPS} over
 * its first {@link #WINDOW_NANOS}, or has a single frame longer than {@link #MAX_FRAME_NANOS}. At a
 * frame every few seconds the game hardly reads its input, so the player could not cancel either. The
 * first {@link #WARM_UP_FRAMES} frames of a step are not judged: switching the renderer can compile
 * shaders or upload meshes once. Timed by the frames themselves, since client ticks bunch up when
 * frames are slow. Render thread only.
 */
final class StepWatchdog {

    static final double MIN_FPS = 5.0;
    static final long WINDOW_NANOS = 2_000_000_000L;
    static final long MAX_FRAME_NANOS = 2_000_000_000L;
    static final int WARM_UP_FRAMES = 2;

    private boolean armed;
    private int seen;
    private int frames;
    private long first;
    private long last;
    private boolean windowJudged;
    @Nullable
    private String verdict;

    /** Starts watching a new step. */
    void start() {
        this.armed = true;
        this.seen = 0;
        this.frames = 0;
        this.windowJudged = false;
        this.verdict = null;
    }

    void stop() {
        this.armed = false;
    }

    /** A frame started at {@code nanos}. */
    void frameStarted(long nanos) {
        if (!this.armed || this.verdict != null) {
            return;
        }
        if (this.seen++ < WARM_UP_FRAMES) {
            this.first = nanos;
            this.last = nanos;
            return;
        }
        long frame = nanos - this.last;
        this.last = nanos;
        this.frames++;
        double fps = this.frames / ((nanos - this.first) / 1e9);
        if (frame > MAX_FRAME_NANOS) {
            this.verdict = String.format(Locale.ROOT, "too slow (%.1f FPS, a frame took %.1f s)", fps, frame / 1e9);
        } else if (!this.windowJudged && nanos - this.first >= WINDOW_NANOS) {
            this.windowJudged = true;
            if (fps < MIN_FPS) {
                this.verdict = String.format(Locale.ROOT, "too slow (%.1f FPS)", fps);
            }
        }
    }

    /** Why the step should stop, or null while it runs fast enough. */
    @Nullable
    String verdict() {
        return this.verdict;
    }
}
