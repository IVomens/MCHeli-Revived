package com.ivomens.mcheli.api.client;

/**
 * Where on the HUD a {@link HudWidget} goes. Each anchor is a slot of the screen that MCHeli's own HUD leaves
 * free in its current layout; the widgets of one anchor are stacked away from the screen edge or corner, in
 * their {@link HudWidget#order() order}, 4 HUD units apart. A widget that is wider than its slot, or that no
 * longer fits next to the widgets stacked before it, is not drawn in that frame, so widgets never overlap each
 * other or the readouts of the modern HUD. docs/API.md lists where every slot is and how large it is.
 *
 * <p>The legacy HUD draws whatever its script files say, so there the slots hug the screen edges and only keep
 * widgets apart from each other.
 *
 * <p><b>Stability.</b> Since API 1.1. Immutable; any thread. A minor version may add constants, so a
 * {@code switch} over this enum needs a {@code default} branch.
 */
public enum HudAnchor {
    /** The top left corner, stacked downwards; left-aligned. */
    TOP_LEFT,
    /** The top right corner, stacked downwards; right-aligned. */
    TOP_RIGHT,
    /** The left edge between the speed tape and the pitch ladder, stacked downwards; left-aligned. */
    LEFT,
    /** The right edge between the pitch ladder and the altitude tape, stacked downwards; right-aligned. */
    RIGHT,
    /** The bottom left corner, above the system bars, stacked upwards; left-aligned. */
    BOTTOM_LEFT,
    /** The bottom right corner, above the weapon card, stacked upwards; right-aligned. */
    BOTTOM_RIGHT,
    /** The bottom edge in the middle, below the pitch ladder, stacked upwards; centred. */
    BOTTOM
}
