package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.util.ModLogger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.glfw.GLFWJoystickCallback;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Joysticks and gamepads as GLFW (bundled with Minecraft) reports them. Devices are enumerated on
 * every call, so one plugged in or out is noticed by the next read; a joystick callback logs each
 * connection and disconnection and passes the event on to any callback installed before it. A device
 * that vanishes while it is read gives no reading instead of an error.
 *
 * <p>Only the render thread uses it, and only once a joystick is enabled in the config; until then
 * nothing touches GLFW's joystick functions.
 */
public final class GlfwJoystickSource implements JoystickSource {

    @Nullable
    private GLFWJoystickCallback callback;
    @Nullable
    private GLFWJoystickCallback previous;

    @Override
    public List<Device> devices() {
        listen();
        List<Device> devices = new ArrayList<>();
        try {
            for (int id = GLFW.GLFW_JOYSTICK_1; id <= GLFW.GLFW_JOYSTICK_LAST; id++) {
                if (GLFW.glfwJoystickPresent(id)) {
                    devices.add(describe(id));
                }
            }
        } catch (RuntimeException e) {
            ModLogger.warn("Could not list the joysticks: {}", e.toString());
        }
        return devices;
    }

    @Override
    @Nullable
    public Reading read(Device device, boolean asGamepad) {
        try {
            return asGamepad && device.gamepad() ? readGamepad(device.id()) : readJoystick(device.id());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Device describe(int id) {
        String name = GLFW.glfwGetJoystickName(id);
        String guid = GLFW.glfwGetJoystickGUID(id);
        return new Device(id, name != null ? name : "Joystick " + (id + 1), guid != null ? guid : "",
                GLFW.glfwJoystickIsGamepad(id));
    }

    @Nullable
    private static Reading readJoystick(int id) {
        FloatBuffer axes = GLFW.glfwGetJoystickAxes(id);
        ByteBuffer buttons = GLFW.glfwGetJoystickButtons(id);
        if (axes == null || buttons == null) {
            return null;
        }
        float[] axisValues = new float[axes.remaining()];
        axes.get(axisValues);
        boolean[] pressed = new boolean[buttons.remaining()];
        for (int i = 0; i < pressed.length; i++) {
            pressed[i] = buttons.get(buttons.position() + i) == GLFW.GLFW_PRESS;
        }
        return new Reading(axisValues, pressed);
    }

    @Nullable
    private static Reading readGamepad(int id) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState state = GLFWGamepadState.malloc(stack);
            if (!GLFW.glfwGetGamepadState(id, state)) {
                return null;
            }
            float[] axes = new float[GLFW.GLFW_GAMEPAD_AXIS_LAST + 1];
            for (int i = 0; i < axes.length; i++) {
                axes[i] = state.axes(i);
            }
            boolean[] buttons = new boolean[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
            for (int i = 0; i < buttons.length; i++) {
                buttons[i] = state.buttons(i) == GLFW.GLFW_PRESS;
            }
            return new Reading(axes, buttons);
        }
    }

    /** Installs the connection callback once. */
    private void listen() {
        if (this.callback != null) {
            return;
        }
        this.callback = GLFWJoystickCallback.create(this::onConnection);
        try {
            this.previous = GLFW.glfwSetJoystickCallback(this.callback);
        } catch (RuntimeException e) {
            ModLogger.warn("Could not listen for joystick connections: {}", e.toString());
        }
    }

    private void onConnection(int id, int event) {
        try {
            if (event == GLFW.GLFW_CONNECTED) {
                ModLogger.info("Joystick connected: {}", describe(id));
            } else if (event == GLFW.GLFW_DISCONNECTED) {
                ModLogger.info("Joystick {} disconnected", id + 1);
            }
        } catch (RuntimeException e) {
            ModLogger.warn("Joystick event {} for {}: {}", event, id, e.toString());
        }
        if (this.previous != null) {
            this.previous.invoke(id, event);
        }
    }
}
