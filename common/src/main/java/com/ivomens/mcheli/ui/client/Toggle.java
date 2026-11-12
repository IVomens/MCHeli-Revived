package com.ivomens.mcheli.ui.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * An on/off button labelled "{@code label : ON}". It shows the value it reads and writes the flipped
 * value on every press, so the state lives with the owner, not in the button.
 */
public final class Toggle extends Button {

    private final Component label;
    private final BooleanSupplier getter;
    private final BooleanConsumer setter;

    Toggle(int width, int height, Component label, BooleanSupplier getter, BooleanConsumer setter) {
        super(0, 0, width, height, label, button -> {
        }, DEFAULT_NARRATION);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.refresh();
    }

    @Override
    public void onPress() {
        this.setter.accept(!this.getter.getAsBoolean());
        this.refresh();
    }

    /** Shows the owner's current value again, after the owner changed it. */
    public void refresh() {
        this.setMessage(Component.translatable("mcheli.ui.toggle", this.label,
                this.getter.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
    }
}
