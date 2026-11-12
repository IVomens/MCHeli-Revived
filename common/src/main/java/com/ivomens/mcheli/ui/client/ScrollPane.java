package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A window onto content taller than itself. The content is the widgets added with {@link #add} at
 * offsets from the content's top-left corner, plus whatever its {@link Painter} draws; both move with
 * the scroll position and are clipped to the window. The wheel scrolls by {@link #step} pixels, and a
 * thin indicator at the right edge shows the position when the content does not fit.
 * <p>
 * The pane owns its widgets: they are not the screen's children, so the pane draws them, passes them
 * the mouse, and gives the keyboard to the text field clicked last.
 */
public final class ScrollPane extends AbstractWidget {

    /** Draws the non-widget content with its top-left corner at {@code (left, top)}. */
    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphics graphics, int left, int top, int mouseX, int mouseY);
    }

    private record Child(AbstractWidget widget, int offsetX, int offsetY) {
    }

    private final Theme theme;
    private final List<Child> children = new ArrayList<>();
    private Painter painter = (graphics, left, top, mouseX, mouseY) -> {
    };
    private IntConsumer onScroll = scroll -> {
    };
    private int contentHeight;
    private int step;
    private int scroll;
    private boolean indicator = true;
    private AbstractWidget pressed;
    private EditBox focused;

    ScrollPane(Theme theme, int width, int height) {
        super(0, 0, width, height, Component.empty());
        this.theme = theme;
        this.step = theme.rowHeight();
    }

    /** Adds a widget to the content at an offset from its top-left corner. */
    public <W extends AbstractWidget> W add(W widget, int offsetX, int offsetY) {
        this.children.add(new Child(widget, offsetX, offsetY));
        this.contentHeight = Math.max(this.contentHeight, offsetY + widget.getHeight());
        return widget;
    }

    /** The pane's widgets, in the order they were added. */
    public List<AbstractWidget> widgets() {
        return this.children.stream().map(Child::widget).toList();
    }

    /** The full height of the content; at least the lowest widget. */
    public ScrollPane contentHeight(int height) {
        this.contentHeight = Math.max(this.contentHeight, height);
        return this;
    }

    public ScrollPane painter(Painter painter) {
        this.painter = painter;
        return this;
    }

    /** Pixels per wheel notch. */
    public ScrollPane step(int px) {
        this.step = px;
        return this;
    }

    /** Whether the thin indicator is drawn; off when the screen shows its own {@link ScrollBar}. */
    public ScrollPane indicator(boolean shown) {
        this.indicator = shown;
        return this;
    }

    /** Called with the new position whenever the pane scrolls. */
    public ScrollPane onScroll(IntConsumer listener) {
        this.onScroll = listener;
        return this;
    }

    /** Gives the keyboard to one of the pane's text fields, or to none. */
    public void focus(@Nullable EditBox field) {
        if (this.focused != null) {
            this.focused.setFocused(false);
        }
        this.focused = field;
        if (field != null) {
            field.setFocused(true);
        }
    }

    @Nullable
    public EditBox focusedField() {
        return this.focused;
    }

    /** Typing goes to the focused text field, wherever the screen's own focus is. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.focused != null && this.focused.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return this.focused != null && this.focused.charTyped(character, modifiers);
    }

    public int scroll() {
        return this.scroll;
    }

    public int maxScroll() {
        return Math.max(0, this.contentHeight - this.height);
    }

    public void setScroll(int scroll) {
        int clamped = Mth.clamp(scroll, 0, this.maxScroll());
        if (clamped != this.scroll) {
            this.scroll = clamped;
            this.onScroll.accept(clamped);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean inside = this.isMouseOver(mouseX, mouseY);
        int contentMouseX = inside ? mouseX : -1;
        int contentMouseY = inside ? mouseY : -1;
        int top = this.getY() - this.scroll;
        graphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
        this.placeChildren();
        this.painter.paint(graphics, this.getX(), top, contentMouseX, contentMouseY);
        for (Child child : this.children) {
            if (this.shows(child.widget())) {
                child.widget().render(graphics, contentMouseX, contentMouseY, partialTick);
            }
        }
        graphics.disableScissor();
        if (this.indicator) {
            ScrollIndicator.draw(graphics, this.theme, this.getRight() - ScrollIndicator.WIDTH - 2, this.getY(),
                    this.getBottom(), this.scroll, this.height, this.contentHeight);
        }
    }

    private void placeChildren() {
        for (Child child : this.children) {
            child.widget().setPosition(this.getX() + child.offsetX(), this.getY() + child.offsetY() - this.scroll);
        }
    }

    private boolean shows(AbstractWidget widget) {
        return widget.getBottom() > this.getY() && widget.getY() < this.getBottom();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.placeChildren();
        for (Child child : this.children) {
            AbstractWidget widget = child.widget();
            if (this.shows(widget) && widget.mouseClicked(mouseX, mouseY, button)) {
                this.pressed = widget;
                if (widget instanceof EditBox field) {
                    this.focus(field);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AbstractWidget widget = this.pressed;
        this.pressed = null;
        return widget != null && widget.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.pressed != null && this.pressed.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY) || scrollY == 0 || this.maxScroll() == 0) {
            return false;
        }
        this.setScroll(this.scroll - (int) Math.signum(scrollY) * this.step);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
