package com.ivomens.mcheli.ui.client;

import com.ivomens.mcheli.util.Ids;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/**
 * The mod's textured vertical scroll bar, from the top position {@code 0} down to {@link #max()}.
 * Dragging the knob or turning the wheel over it moves it a whole position at a time and reports the
 * new position.
 */
public final class ScrollBar extends AbstractWidget {

    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/gui/widgets.png");
    private static final int TRACK_U = 46;
    private static final int TRACK_BOTTOM_V = 200;
    private static final int KNOB_U = 66;
    private static final int KNOB_BOTTOM_V = 196;
    private static final int KNOB_HALF = 4;

    private final IntConsumer onScroll;
    private int max;
    private int position;

    ScrollBar(int width, int height, IntConsumer onScroll) {
        super(0, 0, width, height, Component.empty());
        this.onScroll = onScroll;
    }

    public int position() {
        return this.position;
    }

    public int max() {
        return this.max;
    }

    /** The last position, and the current one clamped to it; reported only if it moved. */
    public void setMax(int max) {
        this.max = Math.max(0, max);
        this.setPosition(this.position);
    }

    /** Moves to a position, clamped to the bar; reported only if it moved. */
    public void setPosition(int position) {
        int clamped = Mth.clamp(position, 0, this.max);
        if (clamped != this.position) {
            this.position = clamped;
            this.onScroll.accept(clamped);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        g.blit(WIDGETS, x, y, TRACK_U, 0, this.width, this.height / 2);
        g.blit(WIDGETS, x, y + this.height / 2, TRACK_U, TRACK_BOTTOM_V - this.height / 2, this.width, this.height / 2);
        double fraction = this.max == 0 ? 0.0 : (double) this.position / this.max;
        int knobY = y + (int) (fraction * (this.height - 2 * KNOB_HALF));
        g.blit(WIDGETS, x, knobY, KNOB_U, 0, this.width, KNOB_HALF);
        g.blit(WIDGETS, x, knobY + KNOB_HALF, KNOB_U, KNOB_BOTTOM_V, this.width, KNOB_HALF);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.dragTo(mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.dragTo(mouseY);
    }

    private void dragTo(double mouseY) {
        double fraction = Mth.clamp((mouseY - (this.getY() + KNOB_HALF)) / (this.height - 2 * KNOB_HALF), 0.0, 1.0);
        this.setPosition((int) Math.round(fraction * this.max));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY) || scrollY == 0) {
            return false;
        }
        this.setPosition(this.position - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
