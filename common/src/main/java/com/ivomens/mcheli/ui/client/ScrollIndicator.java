package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.GuiGraphics;

/** The thin, passive scroll bar of {@link ScrollPane} and {@link ListView}: a track and a thumb, 4 px wide. */
final class ScrollIndicator {

    static final int WIDTH = 4;
    private static final int MIN_THUMB = 16;

    private ScrollIndicator() {
    }

    /**
     * Draws the indicator for {@code scroll} of {@code content} units seen through a window {@code visible}
     * units tall, stretched over the {@code top}..{@code bottom} track; nothing when everything fits.
     */
    static void draw(GuiGraphics g, Theme theme, int x, int top, int bottom, int scroll, int visible, int content) {
        int maxScroll = content - visible;
        if (maxScroll <= 0) {
            return;
        }
        int track = bottom - top;
        int thumb = Math.max(MIN_THUMB, track * visible / content);
        int thumbY = top + (int) ((long) scroll * (track - thumb) / maxScroll);
        g.fill(x, top, x + WIDTH, bottom, theme.scrollTrack());
        g.fill(x, thumbY, x + WIDTH, thumbY + thumb, theme.scrollThumb());
    }
}
