package com.ivomens.mcheli.core.input;

/**
 * "Move the axis you want" and "press the button you want" for the binding screen. Capture starts
 * from what the device reports at that moment, so an axis that rests off centre (a throttle lever,
 * a gamepad trigger at -1) or a button already held is not taken by mistake: an axis counts once it
 * has travelled far enough from where it was, a button once it goes down.
 */
public final class InputCapture {

    /** An axis must travel this far from where it was, out of the full -1..1 range. */
    public static final float AXIS_TRAVEL = 0.5F;

    private float[] axisBaseline = new float[0];
    private boolean[] buttonBaseline = new boolean[0];

    /** Remembers the device as it is now. */
    public void start(float[] axes, boolean[] buttons) {
        this.axisBaseline = axes.clone();
        this.buttonBaseline = buttons.clone();
    }

    /** The axis that travelled furthest past {@link #AXIS_TRAVEL}, or -1. */
    public int movedAxis(float[] axes) {
        int best = -1;
        float bestTravel = AXIS_TRAVEL;
        for (int i = 0; i < axes.length; i++) {
            float from = i < this.axisBaseline.length ? this.axisBaseline[i] : 0.0F;
            float travel = Math.abs(axes[i] - from);
            if (travel > bestTravel) {
                best = i;
                bestTravel = travel;
            }
        }
        return best;
    }

    /**
     * The first button that is down now and was not when capture started, or -1. A button held at the
     * start counts once it has been let go and pressed again.
     */
    public int pressedButton(boolean[] buttons) {
        for (int i = 0; i < buttons.length; i++) {
            boolean before = i < this.buttonBaseline.length && this.buttonBaseline[i];
            if (buttons[i] && !before) {
                return i;
            }
            if (!buttons[i] && before) {
                this.buttonBaseline[i] = false;
            }
        }
        return -1;
    }
}
