package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.BoolOption;
import com.ivomens.mcheli.config.ControlSettings;
import com.ivomens.mcheli.config.DebugSettings;
import com.ivomens.mcheli.config.DoubleOption;
import com.ivomens.mcheli.config.HandlingSettings;
import com.ivomens.mcheli.config.HudSettings;
import com.ivomens.mcheli.config.IntOption;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.Option;
import com.ivomens.mcheli.config.RenderSettings;
import com.ivomens.mcheli.ui.client.SliderModel;

import java.util.ArrayList;
import java.util.List;

/**
 * The options as the options screen shows them, from opening the screen until it saves. Saving writes
 * every option the screen shows, edited or not, onto the config as it is then, so options the screen
 * does not show keep whatever changed meanwhile.
 * <p>
 * Slider values start on their sliders' steps and are saved as the sliders show them: sensitivity and
 * the marker sizes cut to one decimal, the hit mark colour as four whole numbers and the texture alpha
 * scaled from 0..255 to 0..1.
 */
final class ConfigDraft {

    static final SliderModel<Float> SENSITIVITY = SliderModel.range(0.0F, 30.0F, 0.1F);
    static final SliderModel<Float> ENTITY_MARKER_SIZE = SliderModel.range(0.0F, 30.0F, 1.0F);
    static final SliderModel<Float> BLOCK_MARKER_SIZE = SliderModel.range(0.0F, 20.0F, 1.0F);
    static final SliderModel<Float> COLOR_CHANNEL = SliderModel.range(0.0F, 255.0F, 16.0F);
    static final SliderModel<Float> TEXTURE_ALPHA = SliderModel.range(0.0F, 255.0F, 1.0F);

    /** The on/off options of the screen. */
    static final List<BoolOption> TOGGLES = List.of(ControlSettings.INVERT_MOUSE, ControlSettings.STICK_MODE_HELI,
            ControlSettings.STICK_MODE_PLANE, ControlSettings.FLIGHT_SIM_MODE,
            ControlSettings.SWITCH_WEAPON_WITH_MOUSE_WHEEL, ControlSettings.AUTO_THROTTLE_DOWN_HELI,
            ControlSettings.AUTO_THROTTLE_DOWN_PLANE, ControlSettings.AUTO_THROTTLE_DOWN_TANK,
            HudSettings.DISPLAY_IN_THIRD_PERSON, RenderSettings.SMOOTH_SHADING, RenderSettings.HIDE_KEYBIND,
            RenderSettings.DISPLAY_ENTITY_MARKER, RenderSettings.REPLACE_RENDER_VIEW_ENTITY,
            RenderSettings.DEFAULT_EXPLOSION_PARTICLE, DebugSettings.TEST_MODE, HandlingSettings.ASSISTED_HELI,
            HandlingSettings.AUTO_RUDDER_PLANE);

    /** The numbers of the handling page, saved with two decimals. */
    static final List<DoubleOption> HANDLING_NUMBERS = List.of(HandlingSettings.AUTO_RUDDER_GAIN,
            HandlingSettings.SMOOTHING_HELI, HandlingSettings.SMOOTHING_PLANE, HandlingSettings.CENTRING_HELI,
            HandlingSettings.CENTRING_PLANE, HandlingSettings.KEY_PITCH_RATE, HandlingSettings.KEY_ROLL_RATE,
            HandlingSettings.KEY_YAW_RATE, HandlingSettings.KEY_RAMP);

    /** The key bindings of the screen, in list order; the name is the end of the label's translation key. */
    static final List<KeyBinding> KEYS = List.of(
            new KeyBinding("up", KeySettings.UP), new KeyBinding("down", KeySettings.DOWN),
            new KeyBinding("right", KeySettings.RIGHT), new KeyBinding("left", KeySettings.LEFT),
            new KeyBinding("switch_gunner", KeySettings.SWITCH_MODE),
            new KeyBinding("switch_hovering", KeySettings.SWITCH_HOVERING),
            new KeyBinding("switch_weapon_1", KeySettings.SWITCH_WEAPON_1),
            new KeyBinding("switch_weapon_2", KeySettings.SWITCH_WEAPON_2),
            new KeyBinding("switch_weapon_mode", KeySettings.SWITCH_WEAPON_MODE),
            new KeyBinding("zoom", KeySettings.ZOOM), new KeyBinding("camera_mode", KeySettings.CAMERA_MODE),
            new KeyBinding("unmount", KeySettings.UNMOUNT), new KeyBinding("flare", KeySettings.FLARE),
            new KeyBinding("extra", KeySettings.EXTRA),
            new KeyBinding("camera_distance_up", KeySettings.CAMERA_DIST_UP),
            new KeyBinding("camera_distance_down", KeySettings.CAMERA_DIST_DOWN),
            new KeyBinding("free_look", KeySettings.FREE_LOOK), new KeyBinding("gui", KeySettings.GUI),
            new KeyBinding("gear", KeySettings.GEAR_UP_DOWN), new KeyBinding("put_to_rack", KeySettings.PUT_TO_RACK),
            new KeyBinding("down_from_rack", KeySettings.DOWN_FROM_RACK),
            new KeyBinding("scoreboard", KeySettings.SCOREBOARD),
            new KeyBinding("multiplay_manager", KeySettings.MULTIPLAY_MANAGER),
            new KeyBinding("airburst_reset", KeySettings.AIRBURST_DIST_RESET),
            new KeyBinding("gps_panel", KeySettings.OPEN_GPS_PANEL),
            new KeyBinding("stick_pitch_up", HandlingSettings.KEY_PITCH_UP),
            new KeyBinding("stick_pitch_down", HandlingSettings.KEY_PITCH_DOWN),
            new KeyBinding("stick_roll_left", HandlingSettings.KEY_ROLL_LEFT),
            new KeyBinding("stick_roll_right", HandlingSettings.KEY_ROLL_RIGHT),
            new KeyBinding("stick_yaw_left", HandlingSettings.KEY_YAW_LEFT),
            new KeyBinding("stick_yaw_right", HandlingSettings.KEY_YAW_RIGHT));

    /** One row of the key binding page. */
    record KeyBinding(String name, IntOption option) {
    }

    /** The channels of the hit mark colour, in slider order. */
    enum HitMarkChannel { ALPHA, RED, GREEN, BLUE }

    private final List<Option<?>> shown = new ArrayList<>();
    private final float[] hitMark;
    private McheliSettings values;

    /**
     * @param config            the config when the screen opens
     * @param developmentTools  whether the screen offers the development page's texture alpha slider
     */
    ConfigDraft(McheliSettings config, boolean developmentTools) {
        this.values = config;
        this.shown.addAll(TOGGLES);
        for (KeyBinding key : KEYS) {
            this.shown.add(key.option());
        }
        this.shown.addAll(HANDLING_NUMBERS);
        this.shown.addAll(List.of(ControlSettings.MOUSE_SENSITIVITY, RenderSettings.ENTITY_MARKER_SIZE,
                RenderSettings.BLOCK_MARKER_SIZE, RenderSettings.HIT_MARK_COLOR));
        this.setSensitivity(onStep(SENSITIVITY, (float) config.controls().mouseSensitivity()));
        this.setEntityMarkerSize(onStep(ENTITY_MARKER_SIZE, (float) config.render().entityMarkerSize()));
        this.setBlockMarkerSize(onStep(BLOCK_MARKER_SIZE, (float) config.render().blockMarkerSize()));
        int rgb = config.render().hitMarkRgb();
        this.hitMark = new float[]{config.render().hitMarkAlpha() * 255.0F, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF,
                rgb & 0xFF};
        for (HitMarkChannel channel : HitMarkChannel.values()) {
            this.setHitMark(channel, onStep(COLOR_CHANNEL, this.hitMark[channel.ordinal()]));
        }
        if (developmentTools) {
            this.shown.add(DebugSettings.TEXTURE_ALPHA);
            this.setTextureAlpha(onStep(TEXTURE_ALPHA, (float) config.debug().textureAlpha() * 255.0F));
        }
    }

    private static float onStep(SliderModel<Float> model, float value) {
        return model.valueAt(model.positionOf(value));
    }

    /** The value truncated (not rounded) to one decimal, as the screen has always saved it. */
    private static double oneDecimal(float value) {
        return (float) (int) (value * 10) / 10;
    }

    boolean get(BoolOption option) {
        return this.values.get(option);
    }

    void set(BoolOption option, boolean value) {
        this.values = this.values.with(option, value);
    }

    int key(IntOption option) {
        return this.values.get(option);
    }

    void setKey(IntOption option, int code) {
        this.values = this.values.with(option, code);
    }

    /** A handling number as its slider shows it. */
    float number(DoubleOption option) {
        return this.values.get(option).floatValue();
    }

    /** A slider's float, kept to the two decimals the file holds. */
    void setNumber(DoubleOption option, float value) {
        this.values = this.values.with(option, Math.round(value * 100.0) / 100.0);
    }

    float sensitivity() {
        return (float) this.values.controls().mouseSensitivity();
    }

    void setSensitivity(float value) {
        this.values = this.values.with(ControlSettings.MOUSE_SENSITIVITY, oneDecimal(value));
    }

    float entityMarkerSize() {
        return (float) this.values.render().entityMarkerSize();
    }

    void setEntityMarkerSize(float value) {
        this.values = this.values.with(RenderSettings.ENTITY_MARKER_SIZE, oneDecimal(value));
    }

    float blockMarkerSize() {
        return (float) this.values.render().blockMarkerSize();
    }

    void setBlockMarkerSize(float value) {
        this.values = this.values.with(RenderSettings.BLOCK_MARKER_SIZE, oneDecimal(value));
    }

    float hitMark(HitMarkChannel channel) {
        return this.hitMark[channel.ordinal()];
    }

    void setHitMark(HitMarkChannel channel, float value) {
        this.hitMark[channel.ordinal()] = value;
        this.values = this.values.with(RenderSettings.HIT_MARK_COLOR, RenderSettings.formatHitMarkColor(
                (int) this.hitMark[0], (int) this.hitMark[1], (int) this.hitMark[2], (int) this.hitMark[3]));
    }

    /** The hit mark colour as ARGB, for the preview. */
    int hitMarkArgb() {
        return (int) this.hitMark[0] << 24 | (int) this.hitMark[1] << 16 | (int) this.hitMark[2] << 8
                | (int) this.hitMark[3];
    }

    float textureAlpha() {
        return (float) this.values.debug().textureAlpha() * 255.0F;
    }

    void setTextureAlpha(float value) {
        this.values = this.values.with(DebugSettings.TEXTURE_ALPHA, value / 255.0);
    }

    /** {@code current} with every option the screen shows set as the screen shows it. */
    McheliSettings applyTo(McheliSettings current) {
        McheliSettings result = current;
        for (Option<?> option : this.shown) {
            result = this.copy(option, result);
        }
        return result;
    }

    private <T> McheliSettings copy(Option<T> option, McheliSettings target) {
        return target.with(option, this.values.get(option));
    }
}
