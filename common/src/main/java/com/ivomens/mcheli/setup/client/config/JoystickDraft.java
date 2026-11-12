package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.BoolOption;
import com.ivomens.mcheli.config.DoubleOption;
import com.ivomens.mcheli.config.IntOption;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.Option;
import com.ivomens.mcheli.core.input.InputCapture;
import com.ivomens.mcheli.vehicle.client.input.JoystickInput;
import com.ivomens.mcheli.vehicle.client.input.JoystickSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The joystick settings as the binding screen edits them, and the device it reads live. Nothing is
 * saved until the screen saves: {@link #applyTo} then writes every joystick option onto the config
 * as it is at that moment.
 * <p>
 * The device is read with the draft's own settings (device choice and gamepad layout) whether or not
 * joysticks are enabled, so a pilot can calibrate before switching them on.
 */
final class JoystickDraft {

    /** The four bindable axes, in screen order. */
    enum AxisSlot {
        PITCH(JoystickSettings.PITCH_AXIS, JoystickSettings.PITCH_INVERT),
        ROLL(JoystickSettings.ROLL_AXIS, JoystickSettings.ROLL_INVERT),
        YAW(JoystickSettings.YAW_AXIS, JoystickSettings.YAW_INVERT),
        THROTTLE(JoystickSettings.THROTTLE_AXIS, JoystickSettings.THROTTLE_INVERT);

        final IntOption index;
        final BoolOption invert;

        AxisSlot(IntOption index, BoolOption invert) {
            this.index = index;
            this.invert = invert;
        }

        String translationName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the screen waits for: an axis to move or a button to go down. */
    record Waiting(@Nullable AxisSlot axis, @Nullable JoystickAction action) {
    }

    private final JoystickSource source;
    private final InputCapture capture = new InputCapture();
    private McheliSettings values;
    @Nullable
    private JoystickSource.Device device;
    private JoystickSource.Reading reading = JoystickSource.Reading.EMPTY;
    @Nullable
    private Waiting waiting;

    JoystickDraft(McheliSettings config, JoystickSource source) {
        this.values = config;
        this.source = source;
    }

    JoystickSettings settings() {
        return this.values.joystick();
    }

    boolean get(BoolOption option) {
        return this.values.get(option);
    }

    void set(BoolOption option, boolean value) {
        this.values = this.values.with(option, value);
    }

    int get(IntOption option) {
        return this.values.get(option);
    }

    float get(DoubleOption option) {
        return this.values.get(option).floatValue();
    }

    void set(DoubleOption option, float value) {
        // the slider's float, as two decimals: what the file will hold
        this.values = this.values.with(option, Math.round(value * 100.0) / 100.0);
    }

    boolean throttleAbsolute() {
        return settings().throttleAbsolute();
    }

    void setThrottleAbsolute(boolean absolute) {
        this.values = this.values.with(JoystickSettings.THROTTLE_MODE, absolute ? "absolute" : "incremental");
    }

    /** Steps the device choice: first connected, then each connected device by name, then round again. */
    void nextDevice() {
        List<JoystickSource.Device> devices = this.source.devices();
        String current = settings().device();
        int at = -1;
        for (int i = 0; i < devices.size(); i++) {
            if (!current.isEmpty() && devices.get(i).name().equals(current)) {
                at = i;
            }
        }
        String next = at + 1 < devices.size() ? devices.get(at + 1).name() : "";
        this.values = this.values.with(JoystickSettings.DEVICE, next);
    }

    /** Reads the chosen device; once per tick and before drawing. Finishes a capture that got its input. */
    void poll() {
        JoystickSettings settings = settings();
        this.device = JoystickInput.select(this.source.devices(), settings.device());
        JoystickSource.Reading read = this.device != null ? this.source.read(this.device, settings.gamepadMapping())
                : null;
        this.reading = read != null ? read : JoystickSource.Reading.EMPTY;
        if (read == null) {
            this.device = null;
        }
        finishCapture();
    }

    @Nullable
    JoystickSource.Device device() {
        return this.device;
    }

    JoystickSource.Reading reading() {
        return this.reading;
    }

    /** Waits for the next axis that moves, which becomes the slot's axis. */
    void captureAxis(AxisSlot slot) {
        this.waiting = new Waiting(slot, null);
        this.capture.start(this.reading.axes(), this.reading.buttons());
    }

    /** Waits for the next button that goes down, which becomes the action's button. */
    void captureButton(JoystickAction action) {
        this.waiting = new Waiting(null, action);
        this.capture.start(this.reading.axes(), this.reading.buttons());
    }

    @Nullable
    Waiting waiting() {
        return this.waiting;
    }

    void cancelCapture() {
        this.waiting = null;
    }

    void clear(AxisSlot slot) {
        this.values = this.values.with(slot.index, -1);
    }

    void clear(JoystickAction action) {
        this.values = this.values.with(action.option(), -1);
    }

    private void finishCapture() {
        Waiting current = this.waiting;
        if (current == null) {
            return;
        }
        if (current.axis() != null) {
            int axis = this.capture.movedAxis(this.reading.axes());
            if (axis >= 0) {
                this.values = this.values.with(current.axis().index, axis);
                this.waiting = null;
            }
        } else if (current.action() != null) {
            int button = this.capture.pressedButton(this.reading.buttons());
            if (button >= 0) {
                this.values = this.values.with(current.action().option(), button);
                this.waiting = null;
            }
        }
    }

    /** {@code current} with every joystick option set as the screen shows it. */
    McheliSettings applyTo(McheliSettings current) {
        McheliSettings result = current;
        for (Option<?> option : JoystickSettings.ALL) {
            result = copy(option, result);
        }
        return result;
    }

    private <T> McheliSettings copy(Option<T> option, McheliSettings target) {
        return target.with(option, this.values.get(option));
    }
}
