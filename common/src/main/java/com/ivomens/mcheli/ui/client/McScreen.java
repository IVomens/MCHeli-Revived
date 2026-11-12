package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base of every MCHeli screen that is not backed by a container menu.
 * <p>
 * A screen builds its widgets in {@link #build()}, which runs when it opens, when the window is resized
 * and after {@link #rebuild()}; state that must survive a rebuild lives in the screen's own fields. It
 * draws in two passes around the widgets: {@link #renderContents} under them, {@link #renderForeground}
 * over them. A click that hits no widget takes the focus away from the focused one, so a text field
 * stops taking keys once the player clicks elsewhere. MCHeli screens do not pause the game unless they
 * say so.
 */
public abstract class McScreen extends Screen {

    protected final Theme theme = Theme.DEFAULT;
    private boolean rebuildRequested;

    protected McScreen(Component title) {
        super(title);
    }

    @Override
    protected final void init() {
        this.build();
    }

    /** Adds the widgets of the current state; the previous ones are already gone. */
    protected abstract void build();

    /** Builds the widgets again before the next frame; safe to call from a widget's handler. */
    protected final void rebuild() {
        this.rebuildRequested = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.rebuildRequested) {
            this.rebuildRequested = false;
            this.rebuildWidgets();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderForeground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.renderContents(graphics, mouseX, mouseY, partialTick);
    }

    /** Draws what belongs under the widgets: panels, labels, values. */
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** Draws what belongs over the widgets: prompts, tooltips. */
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        this.setFocused(null);
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Adds a widget to the screen. */
    protected final <W extends AbstractWidget> W add(W widget) {
        return this.addRenderableWidget(widget);
    }

    /** A column of widgets from {@code (x, y)} that adds each widget to the screen. */
    protected final Layout column(int x, int y) {
        return Layout.column(x, y, this::addRenderableWidget);
    }

    /** A row of widgets from {@code (x, y)} that adds each widget to the screen. */
    protected final Layout row(int x, int y) {
        return Layout.row(x, y, this::addRenderableWidget);
    }

    /** A grid of widgets from {@code (x, y)} that adds each widget to the screen. */
    protected final Layout grid(int x, int y, int columns, int cellWidth, int cellHeight) {
        return Layout.grid(x, y, columns, cellWidth, cellHeight, this::addRenderableWidget);
    }
}
