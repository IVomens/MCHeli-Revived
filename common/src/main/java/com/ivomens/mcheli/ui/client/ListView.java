package com.ivomens.mcheli.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A list of items, one fixed-height row each, of which one can be selected. The wheel scrolls it a
 * row at a time; when there are more items than rows, a thin indicator right of the list shows where
 * the window is. The screen's {@link RowPainter} draws each row's text over the theme's row background.
 *
 * @param <T> the item type
 */
public final class ListView<T> extends AbstractWidget {

    /** Draws one row; {@code (x, y)} is its top-left corner. */
    @FunctionalInterface
    public interface RowPainter<T> {
        void paint(GuiGraphics graphics, T item, int x, int y, int width, boolean selected, boolean hovered);
    }

    private static final int FRAME = 2;

    private final Theme theme;
    private final int rowHeight;
    private final RowPainter<? super T> painter;
    private final Consumer<? super T> onSelect;
    private List<? extends T> items = List.of();
    private int selected = -1;
    private int scroll;
    private boolean framed = true;
    private boolean rowBackgrounds = true;

    ListView(Theme theme, int width, int rows, int rowHeight, RowPainter<? super T> painter,
             Consumer<? super T> onSelect) {
        super(0, 0, width, rows * rowHeight, Component.empty());
        this.theme = theme;
        this.rowHeight = rowHeight;
        this.painter = painter;
        this.onSelect = onSelect;
    }

    /** Whether a dark frame is drawn around the list; on by default. */
    public ListView<T> framed(boolean framed) {
        this.framed = framed;
        return this;
    }

    /** Whether the theme's hover and selection backgrounds are drawn; off when the rows paint their own. */
    public ListView<T> rowBackgrounds(boolean drawn) {
        this.rowBackgrounds = drawn;
        return this;
    }

    /** Shows these items, keeping the selection if its index is still in range. */
    public ListView<T> setItems(List<? extends T> items) {
        this.items = items;
        if (this.selected >= items.size()) {
            this.selected = -1;
        }
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
        return this;
    }

    /** Selects the item at {@code index} (or nothing for -1) without telling the owner. */
    public ListView<T> select(int index) {
        this.selected = index >= 0 && index < this.items.size() ? index : -1;
        return this;
    }

    public int selectedIndex() {
        return this.selected;
    }

    @Nullable
    public T selected() {
        return this.selected >= 0 ? this.items.get(this.selected) : null;
    }

    /** The item of the row under the mouse, or null. */
    @Nullable
    public T itemAt(double mouseX, double mouseY) {
        int index = this.indexAt(mouseX, mouseY);
        return index >= 0 ? this.items.get(index) : null;
    }

    private int rows() {
        return this.height / this.rowHeight;
    }

    private int maxScroll() {
        return Math.max(0, this.items.size() - this.rows());
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return -1;
        }
        int index = this.scroll + (int) (mouseY - this.getY()) / this.rowHeight;
        return index < this.items.size() ? index : -1;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        if (this.framed) {
            graphics.fill(x - FRAME, y - FRAME, this.getRight() + FRAME, this.getBottom() + FRAME, this.theme.panel());
        }
        int hovered = this.indexAt(mouseX, mouseY);
        int shown = Math.min(this.rows(), this.items.size() - this.scroll);
        for (int i = 0; i < shown; i++) {
            int index = this.scroll + i;
            int rowY = y + i * this.rowHeight;
            if (this.rowBackgrounds) {
                int background = index == this.selected ? this.theme.rowSelected()
                        : index == hovered ? this.theme.rowHovered() : this.theme.row();
                graphics.fill(x, rowY, this.getRight(), rowY + this.rowHeight - 1, background);
            }
            this.painter.paint(graphics, this.items.get(index), x, rowY, this.width, index == this.selected,
                    index == hovered);
        }
        ScrollIndicator.draw(graphics, this.theme, this.getRight() + FRAME + 1, y, this.getBottom(), this.scroll,
                this.rows(), this.items.size());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active || button != 0) {
            return false;
        }
        int index = this.indexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        this.selected = index;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.onSelect.accept(this.items.get(index));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY) || scrollY == 0) {
            return false;
        }
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, this.maxScroll());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
