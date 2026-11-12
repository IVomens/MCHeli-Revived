package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.BoolOption;
import com.ivomens.mcheli.config.DoubleOption;
import com.ivomens.mcheli.config.HandlingSettings;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.SliderModel;
import com.ivomens.mcheli.ui.client.Widgets;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * The handling page of the options screen: the helicopter assist, the planes' auto-rudder, stick
 * smoothing and centring, and the rates of the stick keys (bound on the key page). Every option is
 * off or at its default until changed, and the tooltips say what each one does.
 */
final class HandlingPage {

    private static final SliderModel<Float> GAIN = SliderModel.range(0.0F, 2.0F, 0.05F);
    private static final SliderModel<Float> SMOOTHING = SliderModel.range(0.0F, 0.5F, 0.01F);
    private static final SliderModel<Float> CENTRING = SliderModel.range(0.0F, 10.0F, 0.25F);
    private static final SliderModel<Float> KEY_RATE = SliderModel.range(0.0F, 120.0F, 1.0F);
    private static final SliderModel<Float> RAMP = SliderModel.range(0.0F, 2.0F, 0.05F);

    private final ConfigDraft draft;

    HandlingPage(ConfigDraft draft) {
        this.draft = draft;
    }

    /**
     * Adds the options in two columns.
     *
     * @param left  the left column, which ends with {@code back}
     * @param right the right column
     */
    void build(Layout left, Layout right, int width, int height, AbstractWidget back) {
        left.add(toggle("assisted_heli", HandlingSettings.ASSISTED_HELI, width, height));
        left.add(toggle("auto_rudder", HandlingSettings.AUTO_RUDDER_PLANE, width, height));
        left.add(slider("auto_rudder_gain", GAIN, HandlingSettings.AUTO_RUDDER_GAIN, "%.2f", width, height));
        left.add(slider("smoothing_heli", SMOOTHING, HandlingSettings.SMOOTHING_HELI, "%.2f s", width, height));
        left.add(slider("smoothing_plane", SMOOTHING, HandlingSettings.SMOOTHING_PLANE, "%.2f s", width, height));
        left.add(back);
        right.add(slider("centring_heli", CENTRING, HandlingSettings.CENTRING_HELI, "%.2f", width, height));
        right.add(slider("centring_plane", CENTRING, HandlingSettings.CENTRING_PLANE, "%.2f", width, height));
        right.add(slider("key_pitch_rate", KEY_RATE, HandlingSettings.KEY_PITCH_RATE, "%.0f", width, height));
        right.add(slider("key_roll_rate", KEY_RATE, HandlingSettings.KEY_ROLL_RATE, "%.0f", width, height));
        right.add(slider("key_yaw_rate", KEY_RATE, HandlingSettings.KEY_YAW_RATE, "%.0f", width, height));
        right.add(slider("key_ramp", RAMP, HandlingSettings.KEY_RAMP, "%.2f s", width, height));
    }

    private AbstractWidget toggle(String name, BoolOption option, int width, int height) {
        AbstractWidget toggle = Widgets.toggle(Component.translatable("mcheli.config.handling." + name), width, height,
                () -> this.draft.get(option), value -> this.draft.set(option, value));
        return withTooltip(toggle, name);
    }

    private AbstractWidget slider(String name, SliderModel<Float> model, DoubleOption option, String format, int width,
                                  int height) {
        Function<Float, Component> label = value -> Component.translatable("mcheli.config.handling." + name,
                String.format(format, value));
        AbstractWidget slider = Widgets.slider(width, height, model, this.draft.number(option), label,
                value -> this.draft.setNumber(option, value));
        return withTooltip(slider, name);
    }

    private static AbstractWidget withTooltip(AbstractWidget widget, String name) {
        widget.setTooltip(Tooltip.create(Component.translatable("mcheli.config.handling." + name + ".tooltip")));
        return widget;
    }
}
