package com.ivomens.mcheli.bench.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The progress box in the top left corner while the benchmark runs. */
final class BenchmarkOverlay {

    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH = 200;
    private static final int BAR_HEIGHT = 3;
    private static final int BACKGROUND = 0xA0000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB0B0B0;
    private static final int BAR = 0xFF55FF55;
    private static final int BAR_BACKGROUND = 0xFF404040;

    private BenchmarkOverlay() {
    }

    /** {@code progress} is 0 to 1 over the whole run. */
    static void draw(GuiGraphics graphics, List<Component> lines, double progress) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int height = PADDING * 2 + lines.size() * lineHeight + BAR_HEIGHT + 2;
        int width = MIN_WIDTH;
        for (Component line : lines) {
            width = Math.max(width, font.width(line) + PADDING * 2);
        }
        graphics.fill(MARGIN, MARGIN, MARGIN + width, MARGIN + height, BACKGROUND);
        int y = MARGIN + PADDING;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), MARGIN + PADDING, y, i == lines.size() - 1 ? DIM : TEXT, false);
            y += lineHeight;
        }
        int barWidth = width - PADDING * 2;
        graphics.fill(MARGIN + PADDING, y + 1, MARGIN + PADDING + barWidth, y + 1 + BAR_HEIGHT, BAR_BACKGROUND);
        int done = (int) Math.round(barWidth * Math.max(0, Math.min(1, progress)));
        graphics.fill(MARGIN + PADDING, y + 1, MARGIN + PADDING + done, y + 1 + BAR_HEIGHT, BAR);
    }
}
