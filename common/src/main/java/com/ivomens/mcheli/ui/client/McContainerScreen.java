package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base of every MCHeli screen over a real container menu: the aircraft panel and trunk, the UAV station
 * and the drafting table. It works like {@link McScreen}: widgets come from {@link #build()} and
 * {@link #rebuild()}, and the screen draws {@link #renderContents} under the widgets and slots (in
 * screen coordinates) and {@link #renderForeground} over them, under the item tooltips. The
 * vanilla title and inventory labels are not drawn; a screen that wants labels over its slots draws
 * them in {@link #renderLabels}, relative to its top-left corner.
 */
public abstract class McContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    protected final Theme theme = Theme.DEFAULT;
    private boolean rebuildRequested;

    protected McContainerScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected final void init() {
        super.init();
        this.build();
    }

    /** Adds the widgets of the current state; the previous ones are already gone. */
    protected void build() {
    }

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
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected final void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        this.renderContents(graphics, mouseX, mouseY, partialTick);
    }

    /** Draws the panel and everything else under the widgets and slots, in screen coordinates. */
    protected abstract void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Draws what belongs over the widgets and slots; the item tooltips still go on top. */
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
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
}
