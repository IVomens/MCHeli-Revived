package com.ivomens.mcheli.hud.client.widget;

import com.ivomens.mcheli.api.client.HudAnchor;

/**
 * Where each {@link HudAnchor} is in each HUD layout, in the modern HUD's 640 x 360 units. The slots of the modern
 * layouts are the space {@code ModernHud} leaves free when the aircraft flies level: if it draws something new,
 * the slot it lands in shrinks here (and in the table in docs/API.md). The legacy slots hug the screen edges.
 */
final class WidgetSlots {

    /** HUD units between two widgets of one slot. */
    static final float GAP = 4;

    /** How the widgets of a slot line up across it. */
    enum Align { LEFT, RIGHT, CENTER }

    /**
     * A free area of the HUD.
     *
     * @param upward whether widgets are stacked from the bottom of the slot up, rather than from the top down
     */
    record Slot(float x, float y, float width, float height, Align align, boolean upward) {
    }

    /** The modern HUD in a helicopter or plane: heading and speed and altitude tapes, pitch ladder, system bars. */
    private static final Slot[] FLIGHT = {
            new Slot(20, 4, 175, 48, Align.LEFT, false),      // TOP_LEFT: above the position line
            new Slot(445, 4, 175, 48, Align.RIGHT, false),    // TOP_RIGHT: above the vehicle name
            new Slot(76, 100, 90, 160, Align.LEFT, false),    // LEFT: between the speed tape and the ladder
            new Slot(474, 100, 88, 130, Align.RIGHT, false),  // RIGHT: between the ladder and the altitude tape
            new Slot(20, 266, 145, 34, Align.LEFT, true),     // BOTTOM_LEFT: above the system bars
            new Slot(474, 234, 88, 44, Align.RIGHT, true),    // BOTTOM_RIGHT: above the weapon card
            new Slot(175, 304, 290, 52, Align.CENTER, true),  // BOTTOM: below the pitch ladder
    };

    /** The modern HUD's gunner sight, in ground vehicles and the gunner view of aircraft. */
    private static final Slot[] SIGHT = {
            new Slot(20, 100, 136, 72, Align.LEFT, false),    // TOP_LEFT: below speed, hull and fuel
            new Slot(460, 48, 160, 120, Align.RIGHT, false),  // TOP_RIGHT: below the name and heading
            new Slot(20, 176, 136, 120, Align.LEFT, false),   // LEFT: left of the sight's horizontal line
            new Slot(484, 172, 136, 58, Align.RIGHT, false),  // RIGHT: right of the sight's horizontal line
            new Slot(20, 300, 136, 56, Align.LEFT, true),     // BOTTOM_LEFT
            new Slot(470, 234, 150, 44, Align.RIGHT, true),   // BOTTOM_RIGHT: above the weapon card
            new Slot(175, 334, 290, 22, Align.CENTER, true),  // BOTTOM: below the sight's vertical line
    };

    /** The legacy scripted HUDs, which may draw anywhere. */
    private static final Slot[] LEGACY = {
            new Slot(8, 8, 180, 100, Align.LEFT, false),
            new Slot(452, 8, 180, 100, Align.RIGHT, false),
            new Slot(8, 112, 180, 120, Align.LEFT, false),
            new Slot(452, 112, 180, 120, Align.RIGHT, false),
            new Slot(8, 236, 180, 116, Align.LEFT, true),
            new Slot(452, 236, 180, 116, Align.RIGHT, true),
            new Slot(192, 300, 256, 52, Align.CENTER, true),
    };

    private WidgetSlots() {
    }

    /** The slot of {@code anchor} in {@code layout}. */
    static Slot of(HudLayout layout, HudAnchor anchor) {
        Slot[] slots = switch (layout) {
            case FLIGHT -> FLIGHT;
            case SIGHT -> SIGHT;
            case LEGACY -> LEGACY;
        };
        return slots[anchor.ordinal()];
    }
}
