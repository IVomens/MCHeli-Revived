package com.ivomens.mcheli.ui.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * The widgets of the toolkit. Each is created at {@code (0, 0)} with its size; a {@link Layout} or
 * {@code setPosition} puts it in place. Handlers are lambdas: there are no ids to switch over.
 */
public final class Widgets {

    private Widgets() {
    }

    /** A push button. */
    public static Button button(Component label, int width, int height, Runnable onPress) {
        return Button.builder(label, pressed -> onPress.run()).size(width, height).build();
    }

    /** A push button with a tooltip. */
    public static Button button(Component label, Component tooltip, int width, int height, Runnable onPress) {
        Button button = button(label, width, height, onPress);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    /** An on/off button showing and flipping the owner's value. */
    public static Toggle toggle(Component label, int width, int height, BooleanSupplier getter, BooleanConsumer setter) {
        return new Toggle(width, height, label, getter, setter);
    }

    /** A slider over a model's values, labelled by {@code label} and reporting each change. */
    public static <T> Slider<T> slider(int width, int height, SliderModel<T> model, T initial,
                                       Function<? super T, Component> label, Consumer<? super T> onChange) {
        return new Slider<>(width, height, model, initial, label, onChange);
    }

    /** A one-line text field holding at most {@code maxLength} characters. */
    public static EditBox text(int width, int height, int maxLength, Component narration) {
        EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, width, height, narration);
        box.setMaxLength(maxLength);
        return box;
    }

    /** A list of {@code rows} visible rows that reports the item the player selects. */
    public static <T> ListView<T> list(Theme theme, int width, int rows, int rowHeight,
                                       ListView.RowPainter<? super T> painter, Consumer<? super T> onSelect) {
        return new ListView<>(theme, width, rows, rowHeight, painter, onSelect);
    }

    /** A scrolling window onto taller content. */
    public static ScrollPane scrollPane(Theme theme, int width, int height) {
        return new ScrollPane(theme, width, height);
    }

    /** The mod's textured vertical scroll bar, reporting each new position. */
    public static ScrollBar scrollBar(int width, int height, IntConsumer onScroll) {
        return new ScrollBar(width, height, onScroll);
    }

    /** Tabs keyed by {@code K}, starting at {@code selected}, that report the tab the player picks. */
    public static <K> TabBar<K> tabs(Theme theme, K selected, Consumer<? super K> onSelect) {
        return new TabBar<>(theme, selected, onSelect);
    }
}
