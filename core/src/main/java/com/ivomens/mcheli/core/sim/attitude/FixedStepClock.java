package com.ivomens.mcheli.core.sim.attitude;

/**
 * Splits render frames into fixed attitude steps, so handling does not depend on the frame rate.
 * <p>
 * The model runs just ahead of the frame: a frame runs as many steps as it takes for the model to
 * reach or pass the frame's time, and the airframe is drawn between the last two steps at the
 * frame's time. The pilot therefore sees no added lag, and a frame that lands exactly on a step
 * (a 60 FPS client) shows that step as it is.
 *
 * @param aheadSeconds how far the model's newest step is ahead of the last frame, below one step
 */
public record FixedStepClock(float aheadSeconds) {

    public static final float STEP_SECONDS = 1.0F / 60.0F;
    public static final FixedStepClock START = new FixedStepClock(0.0F);
    /** A frame longer than this (a hitch) only steps this far, as the per-frame mode capped it. */
    private static final float MAX_FRAME_SECONDS = 0.1F;

    /**
     * The frame's steps.
     *
     * @param steps    steps to run now
     * @param fraction where the frame falls between the second-newest step (0) and the newest (1)
     * @param clock    the clock after the frame
     */
    public record Frame(int steps, float fraction, FixedStepClock clock) {
    }

    public Frame advance(float frameSeconds) {
        float behind = Math.min(Math.max(frameSeconds, 0.0F), MAX_FRAME_SECONDS) - this.aheadSeconds;
        int steps = behind > 0.0F ? (int) Math.ceil(behind / STEP_SECONDS) : 0;
        float ahead = Math.min(Math.max(steps * STEP_SECONDS - behind, 0.0F), STEP_SECONDS);
        return new Frame(steps, 1.0F - ahead / STEP_SECONDS, new FixedStepClock(ahead));
    }
}
