package com.ivomens.mcheli.core.sim.attitude;

import com.ivomens.mcheli.core.sim.control.ControlInput;

/** Side keys that turn an angle: left down, right up, both or neither leave it alone. */
final class KeyTurn {

    private KeyTurn() {
    }

    /**
     * The angle turned by {@code step} degrees for the held key. An untouched angle is returned as
     * it is, not plus zero, so a negative zero keeps its sign as it always did.
     */
    static float apply(float angle, ControlInput keys, float step) {
        if (keys.leftOnly()) {
            return angle - step;
        }
        if (keys.rightOnly()) {
            return angle + step;
        }
        return angle;
    }
}
