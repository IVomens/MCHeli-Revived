package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.function.Consumer;

/**
 * Places widgets one after the other: down a column, along a row, or cell by cell through a grid, with
 * a gap between them. Each widget keeps its own size and is handed to the layout's sink (usually the
 * screen's widget list) once placed.
 * <pre>{@code
 * Layout left = column(x, y).gap(5);
 * left.add(Widgets.button(label, 150, 20, this::save));
 * }</pre>
 */
public final class Layout {

    private enum Flow { COLUMN, ROW, GRID }

    private final Flow flow;
    private final int originX;
    private final int originY;
    private final int columns;
    private final int cellWidth;
    private final int cellHeight;
    private final Consumer<? super AbstractWidget> sink;
    private int gapX;
    private int gapY;
    private int cursorX;
    private int cursorY;
    private int placed;

    private Layout(Flow flow, int x, int y, int columns, int cellWidth, int cellHeight,
                   Consumer<? super AbstractWidget> sink) {
        this.flow = flow;
        this.originX = x;
        this.originY = y;
        this.columns = columns;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.sink = sink;
        this.cursorX = x;
        this.cursorY = y;
    }

    /** Widgets stacked downwards from {@code (x, y)}. */
    public static Layout column(int x, int y, Consumer<? super AbstractWidget> sink) {
        return new Layout(Flow.COLUMN, x, y, 1, 0, 0, sink);
    }

    /** Widgets side by side to the right of {@code (x, y)}. */
    public static Layout row(int x, int y, Consumer<? super AbstractWidget> sink) {
        return new Layout(Flow.ROW, x, y, 0, 0, 0, sink);
    }

    /** Widgets filling {@code columns} cells of a fixed size per line, left to right and then downwards. */
    public static Layout grid(int x, int y, int columns, int cellWidth, int cellHeight,
                              Consumer<? super AbstractWidget> sink) {
        return new Layout(Flow.GRID, x, y, Math.max(1, columns), cellWidth, cellHeight, sink);
    }

    /** The space between neighbouring widgets, in both directions. */
    public Layout gap(int px) {
        return this.gap(px, px);
    }

    /** The space between neighbouring widgets across ({@code horizontal}) and down ({@code vertical}). */
    public Layout gap(int horizontal, int vertical) {
        this.gapX = horizontal;
        this.gapY = vertical;
        return this;
    }

    /** Moves the widget to the next free place, hands it to the sink and returns it. */
    public <W extends AbstractWidget> W add(W widget) {
        switch (this.flow) {
            case COLUMN -> {
                widget.setPosition(this.cursorX, this.cursorY);
                this.cursorY += widget.getHeight() + this.gapY;
            }
            case ROW -> {
                widget.setPosition(this.cursorX, this.cursorY);
                this.cursorX += widget.getWidth() + this.gapX;
            }
            case GRID -> widget.setPosition(
                    this.originX + this.placed % this.columns * (this.cellWidth + this.gapX),
                    this.originY + this.placed / this.columns * (this.cellHeight + this.gapY));
        }
        this.placed++;
        this.sink.accept(widget);
        return widget;
    }

    /** Leaves {@code px} of empty space before the next widget of a row or column. */
    public Layout space(int px) {
        if (this.flow == Flow.ROW) {
            this.cursorX += px;
        } else {
            this.cursorY += px;
        }
        return this;
    }

    /** Leaves the next cell of a grid empty. */
    public Layout skipCell() {
        this.placed++;
        return this;
    }

    /** Where the next widget of a row or column goes. */
    public int nextX() {
        return this.cursorX;
    }

    /** Where the next widget of a row or column goes. */
    public int nextY() {
        return this.cursorY;
    }
}
