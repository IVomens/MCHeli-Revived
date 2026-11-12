package com.ivomens.mcheli.core.content.def.hud;

/**
 * Two formulas that go together, such as the {@code x, y} of a position or the width and height of a size.
 * A formula the file leaves out of the map spelling is null and draws as 0.
 */
public record HudPoint(String x, String y) {
}
