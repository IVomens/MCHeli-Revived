package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.core.hud.HudFrame;
import com.ivomens.mcheli.core.hud.HudVariable;

/** Test mode: every HUD variable and its value in this frame, in two columns, for HUD authors. */
final class VariablesOverlay {

    private static final int TEXT_COLOR = 0x00CF00;
    private static final int LINE_HEIGHT = 10;

    private VariablesOverlay() {
    }

    static void draw(HudFrame frame, HudCanvas canvas) {
        int width = canvas.width();
        int height = canvas.height();
        int leftColumnX = Math.max(10, (int) (width * 0.10));
        int rightColumnX = (int) Math.min(width - 150, (int) (width * 0.80));
        int startY = Math.max(10, (int) (height * 0.35));
        int valueOffsetX = Math.max(80, (int) (width * 0.01));
        int half = HudVariable.values().length / 2;

        int x = leftColumnX;
        int y = startY;
        for (HudVariable variable : HudVariable.values()) {
            canvas.text(variable.scriptName(), x, y, TEXT_COLOR, true);
            canvas.text(describe(frame, variable), x + valueOffsetX, y, TEXT_COLOR, true);
            y += LINE_HEIGHT;
            if (variable.ordinal() + 1 == half) {
                x = rightColumnX;
                y = startY;
            }
        }
    }

    private static String describe(HudFrame frame, HudVariable variable) {
        if (variable == HudVariable.COLOR) {
            return ": 0x%08X".formatted((int) (long) frame.doubleValue(variable));
        }
        return switch (variable.type()) {
            case BOOL -> ": %s (%.2f)".formatted(frame.boolValue(variable), frame.boolValue(variable) ? 1.0 : 0.0);
            case INT -> ": %.2f".formatted((double) frame.intValue(variable));
            case DOUBLE -> ": %.2f".formatted(frame.doubleValue(variable));
            case NIL -> throw new IllegalStateException("a variable has a value: " + variable);
        };
    }
}
