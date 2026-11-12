package com.ivomens.mcheli.core.input;

/**
 * How one joystick axis is read: inverted or not, a dead zone around the centre, an expo curve that
 * softens small deflections, and a sensitivity that scales the result. The output is -1 to 1, like
 * the input, and reaches the end of its range only at full deflection (or earlier with a sensitivity
 * above one).
 *
 * @param invert      flips the axis
 * @param deadZone    share of the travel around the centre that reads as zero, 0 to 0.9
 * @param expo        0 is linear, 1 is a pure cube; in between it blends the two
 * @param sensitivity scales the shaped value, which is then clamped to -1..1
 */
public record AxisCurve(boolean invert, float deadZone, float expo, float sensitivity) {

    /** A plain axis: no dead zone, linear, full scale. */
    public static final AxisCurve LINEAR = new AxisCurve(false, 0.0F, 0.0F, 1.0F);

    private static final float MAX_DEAD_ZONE = 0.9F;

    public AxisCurve {
        deadZone = Math.clamp(deadZone, 0.0F, MAX_DEAD_ZONE);
        expo = Math.clamp(expo, 0.0F, 1.0F);
        sensitivity = Math.max(0.0F, sensitivity);
    }

    public AxisCurve inverted(boolean flip) {
        return new AxisCurve(flip, this.deadZone, this.expo, this.sensitivity);
    }

    /**
     * Shapes a raw reading. Outside the dead zone the travel is rescaled to start at zero, so there is
     * no jump at its edge.
     */
    public float shape(float raw) {
        float value = Math.clamp(this.invert ? -raw : raw, -1.0F, 1.0F);
        float magnitude = Math.abs(value);
        if (magnitude <= this.deadZone) {
            return 0.0F;
        }
        float travel = (magnitude - this.deadZone) / (1.0F - this.deadZone);
        float curved = (1.0F - this.expo) * travel + this.expo * travel * travel * travel;
        return Math.copySign(Math.min(1.0F, curved * this.sensitivity), value);
    }

    /** Whether a raw reading is outside the dead zone, so the pilot is moving the axis. */
    public boolean deflected(float raw) {
        return Math.abs(Math.clamp(raw, -1.0F, 1.0F)) > this.deadZone;
    }
}
