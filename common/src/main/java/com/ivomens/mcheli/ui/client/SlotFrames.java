package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The sunken frames vanilla draws behind inventory slots, for container screens that lay out their
 * slots in code instead of a background texture.
 */
public final class SlotFrames {

    public static final int SLOT_SIZE = 18;
    private static final int FACE = 0xFF8B8B8B;
    private static final int SHADOW = 0xFF373737;
    private static final int LIGHT = 0xFFFFFFFF;
    /** The hotbar sits this far below the main inventory rows. */
    private static final int HOTBAR_GAP = 4;

    private SlotFrames() {
    }

    /** One slot frame with its top-left corner at {@code (x, y)}. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, FACE);
        g.fill(x, y, x + SLOT_SIZE - 1, y + 1, SHADOW);
        g.fill(x, y, x + 1, y + SLOT_SIZE - 1, SHADOW);
        g.fill(x + 1, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, LIGHT);
        g.fill(x + SLOT_SIZE - 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, LIGHT);
    }

    /** The 27 inventory slots and the hotbar, for slots whose first item sits at {@code (x, y)}. */
    public static void playerInventory(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, x + column * SLOT_SIZE - 1, y + row * SLOT_SIZE - 1);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, x + column * SLOT_SIZE - 1, y + 3 * SLOT_SIZE + HOTBAR_GAP - 1);
        }
    }
}
