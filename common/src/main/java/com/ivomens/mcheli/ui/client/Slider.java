package com.ivomens.mcheli.ui.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A horizontal slider over the values of a {@link SliderModel}, labelled with its current value. The
 * handle always rests on a valid value, and every change is reported to the owner.
 *
 * @param <T> the value type
 */
public final class Slider<T> extends AbstractSliderButton {

    private final SliderModel<T> model;
    private final Function<? super T, Component> label;
    private final Consumer<? super T> onChange;
    private T current;

    Slider(int width, int height, SliderModel<T> model, T initial, Function<? super T, Component> label,
           Consumer<? super T> onChange) {
        super(0, 0, width, height, Component.empty(), model.positionOf(initial));
        this.model = model;
        this.label = label;
        this.onChange = onChange;
        this.current = model.valueAt(this.value);
        this.updateMessage();
    }

    public T value() {
        return this.current;
    }

    @Override
    protected void updateMessage() {
        this.setMessage(this.label.apply(this.current));
    }

    @Override
    protected void applyValue() {
        this.current = this.model.valueAt(this.value);
        this.value = this.model.positionOf(this.current);
        this.onChange.accept(this.current);
    }
}
