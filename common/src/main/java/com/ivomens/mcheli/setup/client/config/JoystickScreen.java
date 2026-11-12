package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.DoubleOption;
import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.setup.client.config.JoystickDraft.AxisSlot;
import com.ivomens.mcheli.ui.client.Layout;
import com.ivomens.mcheli.ui.client.McScreen;
import com.ivomens.mcheli.ui.client.Slider;
import com.ivomens.mcheli.ui.client.SliderModel;
import com.ivomens.mcheli.ui.client.Widgets;
import com.ivomens.mcheli.vehicle.client.input.JoystickSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Joystick and gamepad set-up: which device, which axis does what (press an axis button, then move
 * the axis), inverting, the response of the stick axes with a live preview, the throttle mode, and
 * the buttons of the vehicle actions (press an action's button, then the joystick button). The bars
 * and the preview show the device live, so it doubles as a test of the device. Nothing is saved until
 * "Save & Close"; "Cancel" and Escape go back without saving.
 */
public final class JoystickScreen extends McScreen {

    private enum Page { AXES, BUTTONS }

    static final int PANEL_WIDTH = 400;
    static final int PANEL_HEIGHT = 222;
    private static final int MARGIN = 5;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TOP_ROW = 18;
    private static final int FIRST_AXIS_ROW = 44;
    private static final int AXIS_ROW = 22;
    static final int AXIS_BUTTON_X = 52;
    static final int BAR_X = 212;
    static final int BAR_WIDTH = 90;
    static final int PREVIEW_X = 308;
    private static final int SLIDER_ROW = 138;
    private static final int THROTTLE_ROW = 162;
    private static final int ACTION_ROW = 194;
    private static final int THIRD = 128;
    private static final int ACTIONS_PER_COLUMN = 7;
    private static final int ACTION_COLUMN = 198;
    static final int ACTION_BUTTON_X = 100;
    /** Where the list of buttons held down goes on the buttons page, right of Cancel. */
    private static final int PRESSED_X = 196;
    private static final SliderModel<Float> DEAD_ZONE = SliderModel.range(0.0F, 0.9F, 0.05F);
    private static final SliderModel<Float> EXPO = SliderModel.range(0.0F, 1.0F, 0.05F);
    private static final SliderModel<Float> SENSITIVITY = SliderModel.range(0.1F, 3.0F, 0.1F);
    private static final SliderModel<Float> THROTTLE_RATE = SliderModel.range(0.05F, 5.0F, 0.05F);

    @Nullable
    private final Screen parent;
    private final JoystickDraft draft;
    private final JoystickPreview preview;
    private Page page = Page.AXES;
    private boolean testMode;
    private boolean wasWaiting;
    private int left;
    private int top;

    /**
     * @param parent the screen to return to, or null to close
     * @param source where the devices come from: GLFW in the game, a scripted device in tests
     */
    public JoystickScreen(@Nullable Screen parent, JoystickSource source) {
        super(Component.translatable("mcheli.joystick.title"));
        this.parent = parent;
        this.draft = new JoystickDraft(McheliConfig.get(), source);
        this.preview = new JoystickPreview(this.draft, this.theme);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void build() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        this.draft.poll();
        Layout header = this.row(this.left + MARGIN, this.top + TOP_ROW).gap(MARGIN);
        header.add(Widgets.toggle(Component.translatable("mcheli.joystick.enabled"), 90, BUTTON_HEIGHT,
                () -> this.draft.get(JoystickSettings.ENABLED), value -> this.draft.set(JoystickSettings.ENABLED, value)));
        header.add(Widgets.button(deviceLabel(), 165, BUTTON_HEIGHT, () -> {
            this.draft.nextDevice();
            this.rebuild();
        }));
        header.add(Widgets.toggle(Component.translatable("mcheli.joystick.gamepad_layout"), 125, BUTTON_HEIGHT,
                () -> this.draft.get(JoystickSettings.GAMEPAD_MAPPING),
                value -> this.draft.set(JoystickSettings.GAMEPAD_MAPPING, value)));
        if (this.page == Page.AXES) {
            buildAxes();
        } else {
            buildButtons();
        }
        Layout actions = this.row(this.left + MARGIN, this.top + ACTION_ROW).gap(MARGIN);
        actions.add(Widgets.button(Component.translatable("mcheli.config.save"), 100, BUTTON_HEIGHT, this::save));
        actions.add(Widgets.button(Component.translatable("gui.cancel"), 80, BUTTON_HEIGHT, this::onClose));
        if (this.page == Page.AXES) {
            actions.add(Widgets.toggle(Component.translatable("mcheli.joystick.test"), 100, BUTTON_HEIGHT,
                    () -> this.testMode, value -> this.testMode = value));
        }
    }

    private void buildAxes() {
        for (AxisSlot slot : AxisSlot.values()) {
            int y = this.top + FIRST_AXIS_ROW + slot.ordinal() * AXIS_ROW;
            this.add(Widgets.button(axisLabel(slot), 70, BUTTON_HEIGHT, () -> this.capture(slot)))
                    .setPosition(this.left + AXIS_BUTTON_X, y);
            this.add(Widgets.toggle(Component.translatable("mcheli.joystick.invert"), 80, BUTTON_HEIGHT,
                    () -> this.draft.get(slot.invert), value -> this.draft.set(slot.invert, value)))
                    .setPosition(this.left + AXIS_BUTTON_X + 75, y);
        }
        Layout sliders = this.row(this.left + MARGIN, this.top + SLIDER_ROW).gap(4);
        sliders.add(slider(DEAD_ZONE, JoystickSettings.DEAD_ZONE, "dead_zone", "%.2f"));
        sliders.add(slider(EXPO, JoystickSettings.EXPO, "expo", "%.2f"));
        sliders.add(slider(SENSITIVITY, JoystickSettings.SENSITIVITY, "sensitivity", "%.1f"));
        Layout throttle = this.row(this.left + MARGIN, this.top + THROTTLE_ROW).gap(4);
        throttle.add(Widgets.toggle(Component.translatable("mcheli.joystick.throttle_absolute"), THIRD, BUTTON_HEIGHT,
                this.draft::throttleAbsolute, this.draft::setThrottleAbsolute));
        throttle.add(slider(THROTTLE_RATE, JoystickSettings.THROTTLE_RATE, "throttle_rate", "%.2f"));
        throttle.add(Widgets.button(Component.translatable("mcheli.joystick.page.buttons"), THIRD, BUTTON_HEIGHT,
                () -> this.show(Page.BUTTONS)));
    }

    private void buildButtons() {
        JoystickAction[] actions = JoystickAction.values();
        for (int i = 0; i < actions.length; i++) {
            JoystickAction action = actions[i];
            int x = this.left + MARGIN + i / ACTIONS_PER_COLUMN * ACTION_COLUMN + ACTION_BUTTON_X;
            int y = this.top + FIRST_AXIS_ROW + i % ACTIONS_PER_COLUMN * AXIS_ROW;
            this.add(Widgets.button(buttonLabel(action), 90, BUTTON_HEIGHT, () -> this.capture(action)))
                    .setPosition(x, y);
        }
        this.add(Widgets.button(Component.translatable("mcheli.joystick.page.axes"), THIRD, BUTTON_HEIGHT,
                () -> this.show(Page.AXES))).setPosition(this.left + PANEL_WIDTH - MARGIN - THIRD, this.top + ACTION_ROW);
    }

    private Slider<Float> slider(SliderModel<Float> model, DoubleOption option, String name,
                                 String format) {
        Consumer<Float> onChange = value -> this.draft.set(option, value);
        return Widgets.slider(THIRD, BUTTON_HEIGHT, model, this.draft.get(option),
                value -> Component.translatable("mcheli.joystick." + name, String.format(format, value)), onChange);
    }

    private Component deviceLabel() {
        String device = this.draft.settings().device();
        return device.isEmpty() ? Component.translatable("mcheli.joystick.device.first")
                : Component.translatable("mcheli.joystick.device", device);
    }

    private Component axisLabel(AxisSlot slot) {
        JoystickDraft.Waiting waiting = this.draft.waiting();
        if (waiting != null && waiting.axis() == slot) {
            return Component.translatable("mcheli.joystick.move");
        }
        int index = this.draft.get(slot.index);
        return index < 0 ? Component.translatable("mcheli.joystick.none")
                : Component.translatable("mcheli.joystick.axis", index);
    }

    private Component buttonLabel(JoystickAction action) {
        JoystickDraft.Waiting waiting = this.draft.waiting();
        if (waiting != null && waiting.action() == action) {
            return Component.translatable("mcheli.joystick.press");
        }
        int button = this.draft.get(action.option());
        return button < 0 ? Component.translatable("mcheli.joystick.none")
                : Component.translatable("mcheli.joystick.button", button);
    }

    private void capture(AxisSlot slot) {
        this.draft.captureAxis(slot);
        this.rebuild();
    }

    private void capture(JoystickAction action) {
        this.draft.captureButton(action);
        this.rebuild();
    }

    private void show(Page target) {
        this.draft.cancelCapture();
        this.page = target;
        this.rebuild();
    }

    private void save() {
        McheliConfig.update(this.draft::applyTo);
        McheliConfig.save();
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void tick() {
        this.draft.poll();
        boolean waiting = this.draft.waiting() != null;
        if (this.wasWaiting && !waiting) {
            this.rebuild();
        }
        this.wasWaiting = waiting;
    }

    /** While waiting for an axis or button: Escape cancels, Delete or Backspace unbinds. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        JoystickDraft.Waiting waiting = this.draft.waiting();
        if (waiting == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (waiting.axis() != null) {
                this.draft.clear(waiting.axis());
            } else if (waiting.action() != null) {
                this.draft.clear(waiting.action());
            }
        }
        this.draft.cancelCapture();
        this.rebuild();
        return true;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.draft.poll();
        graphics.fill(this.left, this.top, this.left + PANEL_WIDTH, this.top + PANEL_HEIGHT, this.theme.panel());
        graphics.drawString(this.font, this.title, this.left + MARGIN, this.top + 5, this.theme.title());
        this.preview.status(graphics, this.font, this.left + 130, this.top + 5);
        if (this.page == Page.AXES) {
            this.preview.axes(graphics, this.font, this.left, this.top + FIRST_AXIS_ROW, AXIS_ROW, this.testMode);
        } else {
            this.preview.buttons(graphics, this.font, this.left + MARGIN, this.top + FIRST_AXIS_ROW, AXIS_ROW,
                    ACTIONS_PER_COLUMN, ACTION_COLUMN, this.left + PRESSED_X, this.top + ACTION_ROW + 6);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        JoystickDraft.Waiting waiting = this.draft.waiting();
        if (waiting != null) {
            this.preview.prompt(graphics, this.font, waiting, this.left, this.top + ACTION_ROW - 12);
        }
    }
}
