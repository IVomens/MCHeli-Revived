package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.hud.HudFrame;
import com.ivomens.mcheli.core.hud.HudVariable;

import java.util.Map;

/** Turns the typed variable maps of the test grids into {@link HudFrame}s. */
final class HudFrames {

    private HudFrames() {
    }

    /**
     * A frame holding {@code values}, each written through the setter of its Java type, so a value whose type
     * is not its variable's type fails the test instead of being converted.
     */
    static HudFrame of(Map<String, Object> values) {
        HudFrame frame = new HudFrame();
        values.forEach((name, value) -> {
            HudVariable variable = HudVariable.named(name)
                    .orElseThrow(() -> new IllegalArgumentException("no HUD variable " + name));
            switch (value) {
                case Integer i -> frame.set(variable, (long) i);
                case Long l -> frame.set(variable, (long) l);
                case Float f -> frame.set(variable, (double) f);
                case Double d -> frame.set(variable, (double) d);
                case Boolean b -> frame.set(variable, (boolean) b);
                default -> throw new IllegalArgumentException(name + " has a value of no variable type: " + value);
            }
        });
        return frame;
    }
}
