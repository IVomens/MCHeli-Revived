package com.ivomens.mcheli.core.input;

/**
 * A pair of keys read as one stick axis with acceleration: holding a key moves the axis towards its
 * end over the ramp time, releasing both (or pressing both) drops it back to zero at once, and the
 * other key reverses it from where it is. With a ramp time of zero a key jumps straight to full.
 * <p>
 * One per axis; fed every frame with the frame's length.
 */
public final class KeyRamp {

    private float value;

    /**
     * @param negative    the key towards -1 is held
     * @param positive    the key towards 1 is held
     * @param dtSeconds   time since the last update
     * @param rampSeconds time from centre to full deflection
     * @return the axis, -1 to 1
     */
    public float update(boolean negative, boolean positive, float dtSeconds, float rampSeconds) {
        if (negative == positive) {
            this.value = 0.0F;
            return this.value;
        }
        float direction = positive ? 1.0F : -1.0F;
        float step = rampSeconds <= 0.0F ? 2.0F : dtSeconds / rampSeconds;
        this.value = Math.clamp(this.value + direction * step, -1.0F, 1.0F);
        return this.value;
    }

    public float value() {
        return this.value;
    }

    public void reset() {
        this.value = 0.0F;
    }
}
