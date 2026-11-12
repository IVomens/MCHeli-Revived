package com.ivomens.mcheli.vehicle.client.input;

/**
 * Measures the time between rendered frames, which the client attitude models integrate over. A gap
 * longer than {@link #SKIPPED_FRAME_SECONDS} (a pause, a loading screen) is reported as skipped, and no
 * step is longer than {@link #MAX_FRAME_SECONDS}.
 */
final class FrameClock {

    /** A frame gap longer than this means the cursor jump since the last frame is not mouse input. */
    private static final float SKIPPED_FRAME_SECONDS = 0.25F;
    private static final float MAX_FRAME_SECONDS = 0.1F;

    private long lastNanos;
    private boolean skipped;

    /** Starts a frame; returns the seconds since the last one, capped at {@link #MAX_FRAME_SECONDS}. */
    float nextFrame() {
        long now = System.nanoTime();
        if (this.lastNanos == 0) {
            this.lastNanos = now;
        }
        float deltaSeconds = (now - this.lastNanos) / 1_000_000_000.0F;
        this.skipped = deltaSeconds > SKIPPED_FRAME_SECONDS;
        this.lastNanos = now;
        return Math.min(deltaSeconds, MAX_FRAME_SECONDS);
    }

    /** Whether the frame just started came after a gap too long to be a frame. */
    boolean skipped() {
        return this.skipped;
    }
}
