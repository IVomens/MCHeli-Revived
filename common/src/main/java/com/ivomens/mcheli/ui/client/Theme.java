package com.ivomens.mcheli.ui.client;

/**
 * The colours and sizes every MCHeli screen draws with, so the screens look like one family. Colours
 * are ARGB, except where the name says otherwise.
 *
 * @param text          body text on dark backgrounds
 * @param muted         secondary text, such as hints and section titles
 * @param faint         the least important text, such as "(none)" placeholders
 * @param title         screen titles
 * @param accent        the highlight under the selected tab and similar marks
 * @param good          positive states: in range, enabled, healthy
 * @param bad           warnings and errors
 * @param label         text on the light, textured panels of the container screens
 * @param panel         background of row lists and framed areas
 * @param row           background of a list row
 * @param rowHovered    background of the list row under the mouse
 * @param rowSelected   background of the selected list row
 * @param scrollTrack   track of a thin scroll indicator
 * @param scrollThumb   thumb of a thin scroll indicator
 * @param rowHeight     height of a list row
 * @param buttonHeight  height of a standard button
 * @param gap           space between neighbouring controls
 */
public record Theme(int text, int muted, int faint, int title, int accent, int good, int bad, int label, int panel,
                    int row, int rowHovered, int rowSelected, int scrollTrack, int scrollThumb, int rowHeight,
                    int buttonHeight, int gap) {

    /** The look of the mod's screens since 1.7: Minecraft's widgets, white text and a yellow-orange accent. */
    public static final Theme DEFAULT = new Theme(0xFFFFFFFF, 0xFFAAAAAA, 0xFF888888, 0xFFFFFF55, 0xFFFFAA00,
            0xFF55FF55, 0xFFFF5555, 0xFF404040, 0x88000000, 0x00000000, 0x44AAAAAA, 0xAA005599, 0x44FFFFFF,
            0xAAFFFFFF, 22, 20, 5);
}
