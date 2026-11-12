package com.ivomens.mcheli.gametest.controls;

import com.ivomens.mcheli.vehicle.client.input.JoystickSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A joystick that exists only in the tests: its axes and buttons are whatever the test sets, and it
 * can be unplugged. It stands in for GLFW behind {@link JoystickSource}.
 */
public final class ScriptedJoystick implements JoystickSource {

    private final Device device;
    private final float[] axes;
    private final boolean[] buttons;
    private boolean connected = true;

    public ScriptedJoystick(String name, boolean gamepad, int axisCount, int buttonCount) {
        this.device = new Device(0, name, "03000000scripted0000000000000000", gamepad);
        this.axes = new float[axisCount];
        this.buttons = new boolean[buttonCount];
    }

    public ScriptedJoystick axis(int index, float value) {
        this.axes[index] = value;
        return this;
    }

    public ScriptedJoystick button(int index, boolean down) {
        this.buttons[index] = down;
        return this;
    }

    public void setConnected(boolean plugged) {
        this.connected = plugged;
    }

    @Override
    public List<Device> devices() {
        return this.connected ? List.of(this.device) : List.of();
    }

    @Override
    @Nullable
    public Reading read(Device wanted, boolean asGamepad) {
        return this.connected && wanted.equals(this.device) ? new Reading(this.axes.clone(), this.buttons.clone()) : null;
    }
}
