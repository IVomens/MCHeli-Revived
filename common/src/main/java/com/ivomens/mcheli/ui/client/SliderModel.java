package com.ivomens.mcheli.ui.client;

/**
 * The values a {@link Slider} chooses from, and where each sits on its track ({@code 0} at the left
 * end, {@code 1} at the right).
 *
 * @param <T> the value type
 */
public interface SliderModel<T> {

    /** The value at a position of the track, snapped to a valid value. */
    T valueAt(double position);

    /** The position of a value on the track, clamped to the track. */
    double positionOf(T value);

    /**
     * Numbers from {@code min} to {@code max} in steps of {@code step}, counted from zero; a step of
     * zero or less allows any value. The arithmetic is in {@code float}, which the config values these
     * sliders edit have always been rounded through.
     */
    static SliderModel<Float> range(float min, float max, float step) {
        return new SliderModel<>() {
            @Override
            public Float valueAt(double position) {
                return this.snap(min + (max - min) * (float) Math.clamp(position, 0.0, 1.0));
            }

            @Override
            public double positionOf(Float value) {
                return max <= min ? 0.0 : Math.clamp((this.snap(value) - min) / (max - min), 0.0F, 1.0F);
            }

            private float snap(float value) {
                float stepped = step > 0.0F ? step * Math.round(value / step) : value;
                return Math.clamp(stepped, min, Math.max(min, max));
            }
        };
    }
}
