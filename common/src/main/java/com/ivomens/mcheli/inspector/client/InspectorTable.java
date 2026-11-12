package com.ivomens.mcheli.inspector.client;

import com.ivomens.mcheli.inspector.SnapshotDiff.Verdict;
import com.ivomens.mcheli.inspector.client.InspectorRows.Row;
import com.ivomens.mcheli.ui.client.ScrollPane;
import com.ivomens.mcheli.ui.client.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Draws the inspector's rows as a table in a {@link ScrollPane}: key, client value, server value and source,
 * in columns that share the width. Section headers span the table. A mismatch between the two sides is
 * tinted red, a definition value that differs from its default gets an accent bar, and a cell too long for
 * its column ends in an ellipsis and shows in full as a tooltip.
 */
final class InspectorTable implements ScrollPane.Painter {

    static final int ROW_HEIGHT = 11;
    static final int HEADER_HEIGHT = 15;
    private static final int PAD = 4;
    private static final int MARK_WIDTH = 2;
    private static final String ELLIPSIS = "...";
    private static final int STRIPE = 0x14FFFFFF;
    private static final int HEADER_BAND = 0x30FFFFFF;
    private static final int MISMATCH_BAND = 0x40FF4040;
    private static final int SERVER_SAME = 0xFFB8D8B8;

    private final Theme theme;
    private final Font font;
    private final int width;
    private List<Row> rows = List.of();
    private boolean serverColumn;
    private boolean detailColumn;
    @Nullable
    private String hovered;

    InspectorTable(Theme theme, Font font, int width) {
        this.theme = theme;
        this.font = font;
        this.width = width;
    }

    /** The rows to draw next, and which of the optional columns there are. */
    void show(List<Row> shown, boolean server, boolean detail) {
        this.rows = shown;
        this.serverColumn = server;
        this.detailColumn = detail;
    }

    int contentHeight() {
        int height = 0;
        for (Row row : this.rows) {
            height += row.header() ? HEADER_HEIGHT : ROW_HEIGHT;
        }
        return height + PAD;
    }

    /** The full text of the cut-off cell under the mouse in the last frame, or null. */
    @Nullable
    String hoveredText() {
        return this.hovered;
    }

    /** The column titles, in a band at {@code (x, y)} above the table. */
    void paintColumnTitles(GuiGraphics graphics, int x, int y, Component server) {
        int[] columns = columns();
        graphics.fill(x, y, x + this.width, y + ROW_HEIGHT + 2, HEADER_BAND);
        int textY = y + 2;
        graphics.drawString(this.font, Component.translatable("mcheli.inspector.column.key"), x + columns[0], textY,
                this.theme.title(), false);
        Component client = Component.translatable(this.detailColumn ? "mcheli.inspector.column.value"
                : "mcheli.inspector.column.client");
        graphics.drawString(this.font, client, x + columns[1], textY, this.theme.title(), false);
        if (this.serverColumn) {
            graphics.drawString(this.font, server, x + columns[2], textY, this.theme.title(), false);
        }
        if (this.detailColumn) {
            graphics.drawString(this.font, Component.translatable("mcheli.inspector.column.source"), x + columns[3],
                    textY, this.theme.title(), false);
        }
    }

    @Override
    public void paint(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        this.hovered = null;
        int[] columns = columns();
        int y = top;
        int index = 0;
        for (Row row : this.rows) {
            int height = row.header() ? HEADER_HEIGHT : ROW_HEIGHT;
            if (row.header()) {
                paintHeader(graphics, row, left, y);
                index = 0;
            } else {
                paintRow(graphics, row, left, y, columns, index++ % 2 == 1, mouseX, mouseY);
            }
            y += height;
        }
    }

    private void paintHeader(GuiGraphics graphics, Row row, int left, int y) {
        graphics.fill(left, y + 2, left + this.width, y + HEADER_HEIGHT - 1, HEADER_BAND);
        int x = left + PAD;
        graphics.drawString(this.font, row.label(), x, y + 5, this.theme.title(), false);
        if (!row.detail().isEmpty()) {
            int detailX = x + this.font.width(row.label()) + 10;
            graphics.drawString(this.font, fit(row.detail(), left + this.width - PAD - detailX), detailX, y + 5,
                    this.theme.muted(), false);
        }
    }

    private void paintRow(GuiGraphics graphics, Row row, int left, int y, int[] columns, boolean odd, int mouseX,
                          int mouseY) {
        if (row.verdict() == Verdict.MISMATCH) {
            graphics.fill(left, y, left + this.width, y + ROW_HEIGHT, MISMATCH_BAND);
        } else if (odd) {
            graphics.fill(left, y, left + this.width, y + ROW_HEIGHT, STRIPE);
        }
        if (row.marked()) {
            graphics.fill(left, y, left + MARK_WIDTH, y + ROW_HEIGHT, this.theme.accent());
        }
        boolean hoveredRow = mouseY >= y && mouseY < y + ROW_HEIGHT;
        int textY = y + 2;
        cell(graphics, row.label(), left, columns, 0, textY, this.theme.muted(), hoveredRow, mouseX);
        cell(graphics, row.client(), left, columns, 1, textY, row.marked() ? this.theme.accent() : this.theme.text(),
                hoveredRow, mouseX);
        if (this.serverColumn) {
            String server = row.server() == null ? "-" : row.server();
            cell(graphics, server, left, columns, 2, textY, serverColour(row.verdict()), hoveredRow, mouseX);
        }
        if (this.detailColumn) {
            cell(graphics, row.detail(), left, columns, 3, textY, this.theme.faint(), hoveredRow, mouseX);
        }
    }

    private void cell(GuiGraphics graphics, String text, int left, int[] columns, int column, int y, int colour,
                      boolean hoveredRow, int mouseX) {
        int x = left + columns[column];
        int end = left + this.width;
        for (int next = columns.length - 1; next > column; next--) {
            if (columns[next] > 0) {
                end = left + columns[next];
            }
        }
        int room = end - x - PAD;
        String shown = fit(text, room);
        graphics.drawString(this.font, shown, x, y, colour, false);
        if (hoveredRow && mouseX >= x && mouseX < end && !shown.equals(text)) {
            this.hovered = text;
        }
    }

    private int serverColour(Verdict verdict) {
        return switch (verdict) {
            case SAME -> SERVER_SAME;
            case MISMATCH -> this.theme.bad();
            case DRIFT, ONE_SIDED -> this.theme.faint();
        };
    }

    /**
     * Where each column starts, from the table's left edge: key, value, server, source. A column the table
     * does not have starts at 0 and is skipped.
     */
    int[] columns() {
        int usable = this.width - PAD;
        int[] weights = {this.detailColumn ? 30 : 34, this.detailColumn ? 22 : 33,
                this.serverColumn ? (this.detailColumn ? 20 : 33) : 0, this.detailColumn ? 28 : 0};
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int[] starts = new int[4];
        int x = PAD + MARK_WIDTH;
        for (int i = 0; i < weights.length; i++) {
            starts[i] = weights[i] == 0 ? 0 : x;
            x += usable * weights[i] / total;
        }
        return starts;
    }

    /** {@code text} cut to fit {@code room} pixels, ending in an ellipsis when it was cut. */
    String fit(String text, int room) {
        if (this.font.width(text) <= room) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, room - this.font.width(ELLIPSIS))) + ELLIPSIS;
    }
}
