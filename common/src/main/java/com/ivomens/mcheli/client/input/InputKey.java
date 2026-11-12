package com.ivomens.mcheli.client.input;

import com.ivomens.mcheli.config.client.KeyNames;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/**
 * Edge-detecting key state for a config key code (legacy LWJGL2 code space, see {@link KeyNames}).
 * Call {@link #update()} once per tick, then query press and release edges.
 * <p>
 * A key may have a second source that holds it down as well, such as the joystick button bound to
 * the same action: the key then reads as held while either is, and the edges follow the combination,
 * so the action fires once however it was pressed.
 */
public class InputKey {

    /** Config codes below zero are mouse buttons, stored as {@code button - 100}. */
    private static final int MOUSE_BUTTON_OFFSET = 100;

    public final int key;
    private final int glfwKey;
    private final BooleanSupplier alsoHeld;
    private boolean pressed;
    private boolean wasPressed;

    public InputKey(int key) {
        this(key, () -> false);
    }

    /** @param alsoHeld another source that holds the key down, asked once per {@link #update()} */
    public InputKey(int key, BooleanSupplier alsoHeld) {
        this.key = key;
        this.glfwKey = key > 0 ? KeyNames.toGlfw(key) : GLFW.GLFW_KEY_UNKNOWN;
        this.alsoHeld = alsoHeld;
    }

    /** Current state of a config key code, without edge detection. */
    public static boolean isKeyDown(int key) {
        if (key > 0) return isGlfwKeyDown(KeyNames.toGlfw(key));
        return key < 0 && isMouseButtonDown(key + MOUSE_BUTTON_OFFSET);
    }

    private static boolean isGlfwKeyDown(int glfwKey) {
        if (glfwKey <= GLFW.GLFW_KEY_UNKNOWN) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), glfwKey);
    }

    private static boolean isMouseButtonDown(int button) {
        if (button < 0 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) return false;
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    /** True on the tick the key went down. */
    public boolean isKeyDown() {
        return !this.wasPressed && this.pressed;
    }

    /** True while the key is held. */
    public boolean isKeyPress() {
        return this.pressed;
    }

    /** True on the tick the key was released. */
    public boolean isKeyUp() {
        return this.wasPressed && !this.pressed;
    }

    public void update() {
        this.wasPressed = this.pressed;
        this.pressed = keyDown() || this.alsoHeld.getAsBoolean();
    }

    private boolean keyDown() {
        if (this.key == 0) return false;
        return this.key > 0 ? isGlfwKeyDown(this.glfwKey) : isMouseButtonDown(this.key + MOUSE_BUTTON_OFFSET);
    }
}
