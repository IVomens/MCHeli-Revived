package com.ivomens.mcheli.setup.client.config;

import com.ivomens.mcheli.config.JoystickAction;
import com.ivomens.mcheli.config.JoystickSettings;
import com.ivomens.mcheli.core.input.AxisCurve;
import com.ivomens.mcheli.core.input.ThrottleAxis;
import com.ivomens.mcheli.setup.client.config.JoystickDraft.AxisSlot;
import com.ivomens.mcheli.ui.client.Theme;
import com.ivomens.mcheli.vehicle.client.input.JoystickSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The live part of the joystick screen: the device status, a bar per axis (the raw position as a
 * tick, the shaped value as the fill), the response curve with the dead zone and the stick's current
 * position on it, a test box that shows the stick as the airframe would get it, and the state of the
 * bound buttons.
 */
final class JoystickPreview {

    private static final int BAR_HEIGHT = 8;
    private static final int PREVIEW_SIZE = 86;
    /** Where a preview's label sits inside its panel. */
    private static final int LABEL_INSET = 3;
    private static final int BAR_BACKGROUND = 0xFF202020;
    private static final int CENTRE_LINE = 0xFF606060;
    private static final int RAW_MARK = 0xFFFFFFFF;
    private static final int DEAD_ZONE_SHADE = 0x40FF5555;
    private static final int DOT = 3;
    private static final int STATUS_WIDTH = 265;

    private final JoystickDraft draft;
    private final Theme theme;

    JoystickPreview(JoystickDraft draft, Theme theme) {
        this.draft = draft;
        this.theme = theme;
    }

    void status(GuiGraphics graphics, Font font, int x, int y) {
        JoystickSource.Device device = this.draft.device();
        Component text;
        int colour;
        if (device == null) {
            text = Component.translatable("mcheli.joystick.status.none");
            colour = this.theme.bad();
        } else {
            JoystickSource.Reading reading = this.draft.reading();
            text = Component.translatable(device.gamepad() ? "mcheli.joystick.status.gamepad"
                    : "mcheli.joystick.status.joystick", device.name(), reading.axes().length, reading.buttons().length);
            colour = this.theme.good();
        }
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), STATUS_WIDTH), x, y, colour);
    }

    /** The axis rows' labels and bars, then the curve or the test box beside them. */
    void axes(GuiGraphics graphics, Font font, int left, int top, int rowHeight, boolean testMode) {
        JoystickSettings settings = this.draft.settings();
        for (AxisSlot slot : AxisSlot.values()) {
            int y = top + slot.ordinal() * rowHeight;
            graphics.drawString(font, Component.translatable("mcheli.joystick.slot." + slot.translationName()),
                    left + 5, y + 6, this.theme.text());
            JoystickSettings.Axis axis = axis(settings, slot);
            float raw = axis.bound() ? this.draft.reading().axis(axis.index()) : 0.0F;
            if (slot == AxisSlot.THROTTLE) {
                throttleBar(graphics, left + JoystickScreen.BAR_X, y + 6, raw, axis, settings);
            } else {
                bar(graphics, left + JoystickScreen.BAR_X, y + 6, raw, stickCurve(settings).inverted(axis.invert())
                        .shape(raw), axis.bound());
            }
        }
        int x = left + JoystickScreen.PREVIEW_X;
        if (testMode) {
            testBox(graphics, font, x, top, settings);
        } else {
            curve(graphics, font, x, top, settings);
        }
    }

    private static JoystickSettings.Axis axis(JoystickSettings settings, AxisSlot slot) {
        return switch (slot) {
            case PITCH -> settings.pitch();
            case ROLL -> settings.roll();
            case YAW -> settings.yaw();
            case THROTTLE -> settings.throttle();
        };
    }

    private static AxisCurve stickCurve(JoystickSettings settings) {
        return new AxisCurve(false, (float) settings.deadZone(), (float) settings.expo(), (float) settings.sensitivity());
    }

    /** A centred bar: the fill runs from the centre to the shaped value, the white tick is the raw position. */
    private void bar(GuiGraphics graphics, int x, int y, float raw, float shaped, boolean bound) {
        int half = JoystickScreen.BAR_WIDTH / 2;
        graphics.fill(x, y, x + JoystickScreen.BAR_WIDTH, y + BAR_HEIGHT, BAR_BACKGROUND);
        if (!bound) {
            return;
        }
        int centre = x + half;
        int end = centre + Math.round(shaped * half);
        graphics.fill(Math.min(centre, end), y + 1, Math.max(centre, end), y + BAR_HEIGHT - 1, this.theme.accent());
        graphics.fill(centre, y, centre + 1, y + BAR_HEIGHT, CENTRE_LINE);
        int mark = centre + Math.round(Math.clamp(raw, -1.0F, 1.0F) * half);
        graphics.fill(mark, y - 1, mark + 1, y + BAR_HEIGHT + 1, RAW_MARK);
    }

    /** A lever fills from idle; an incremental axis shows its deflection from the centre. */
    private void throttleBar(GuiGraphics graphics, int x, int y, float raw, JoystickSettings.Axis axis,
                             JoystickSettings settings) {
        if (!settings.throttleAbsolute()) {
            AxisCurve curve = new AxisCurve(axis.invert(), (float) settings.deadZone(), 0.0F, 1.0F);
            // forward raises the throttle, so the bar grows to the right when pushed forward
            bar(graphics, x, y, -raw, -curve.shape(raw), axis.bound());
            return;
        }
        graphics.fill(x, y, x + JoystickScreen.BAR_WIDTH, y + BAR_HEIGHT, BAR_BACKGROUND);
        if (axis.bound()) {
            double lever = ThrottleAxis.leverPosition(AxisCurve.LINEAR.inverted(axis.invert()).shape(raw));
            graphics.fill(x, y + 1, x + (int) Math.round(lever * JoystickScreen.BAR_WIDTH), y + BAR_HEIGHT - 1,
                    this.theme.good());
        }
    }

    /** The response curve of the stick axes, the dead zone shaded, the roll axis's position as a dot. */
    private void curve(GuiGraphics graphics, Font font, int x, int y, JoystickSettings settings) {
        graphics.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, BAR_BACKGROUND);
        int half = PREVIEW_SIZE / 2;
        int dead = (int) Math.round(settings.deadZone() * half);
        graphics.fill(x + half - dead, y, x + half + dead + 1, y + PREVIEW_SIZE, DEAD_ZONE_SHADE);
        graphics.fill(x + half, y, x + half + 1, y + PREVIEW_SIZE, CENTRE_LINE);
        graphics.fill(x, y + half, x + PREVIEW_SIZE, y + half + 1, CENTRE_LINE);
        AxisCurve curve = stickCurve(settings);
        for (int px = 0; px < PREVIEW_SIZE; px++) {
            float in = px / (float) (PREVIEW_SIZE - 1) * 2.0F - 1.0F;
            int py = y + half - Math.round(curve.shape(in) * (half - 1));
            graphics.fill(x + px, py, x + px + 1, py + 1, this.theme.accent());
        }
        JoystickSettings.Axis roll = settings.roll();
        if (roll.bound() && this.draft.device() != null) {
            float reading = this.draft.reading().axis(roll.index());
            float raw = roll.invert() ? -reading : reading;
            int dx = x + half + Math.round(Math.clamp(raw, -1.0F, 1.0F) * (half - 1));
            int dy = y + half - Math.round(curve.shape(raw) * (half - 1));
            graphics.fill(dx - 1, dy - 1, dx + DOT - 1, dy + DOT - 1, RAW_MARK);
        }
        // the label sits inside the panel, drawn last: above the panel is the row of buttons
        graphics.drawString(font, Component.translatable("mcheli.joystick.curve"), x + LABEL_INSET, y + LABEL_INSET,
                this.theme.muted());
    }

    /** The stick as the airframe gets it: roll across, pitch up and down (pulled back is up), yaw below. */
    private void testBox(GuiGraphics graphics, Font font, int x, int y, JoystickSettings settings) {
        graphics.drawString(font, Component.translatable("mcheli.joystick.test_box"), x, y - 10, this.theme.muted());
        graphics.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, BAR_BACKGROUND);
        int half = PREVIEW_SIZE / 2;
        graphics.fill(x + half, y, x + half + 1, y + PREVIEW_SIZE, CENTRE_LINE);
        graphics.fill(x, y + half, x + PREVIEW_SIZE, y + half + 1, CENTRE_LINE);
        AxisCurve curve = stickCurve(settings);
        float roll = shaped(curve, settings.roll());
        float pitch = shaped(curve, settings.pitch());
        int dx = x + half + Math.round(roll * (half - 2));
        int dy = y + half - Math.round(pitch * (half - 2));
        graphics.fill(dx - 2, dy - 2, dx + 3, dy + 3, this.theme.good());
        float yaw = shaped(curve, settings.yaw());
        int yawX = x + half + Math.round(yaw * (half - 1));
        graphics.fill(Math.min(x + half, yawX), y + PREVIEW_SIZE + 3, Math.max(x + half, yawX) + 1,
                y + PREVIEW_SIZE + 7, this.theme.accent());
    }

    private float shaped(AxisCurve curve, JoystickSettings.Axis axis) {
        return axis.bound() ? curve.inverted(axis.invert()).shape(this.draft.reading().axis(axis.index())) : 0.0F;
    }

    /** Each action's name with a light that is on while its button is down; the raw buttons underneath. */
    void buttons(GuiGraphics graphics, Font font, int left, int top, int rowHeight, int perColumn, int columnWidth,
                 int pressedX, int pressedY) {
        JoystickAction[] actions = JoystickAction.values();
        JoystickSource.Reading reading = this.draft.reading();
        for (int i = 0; i < actions.length; i++) {
            int x = left + i / perColumn * columnWidth;
            int y = top + i % perColumn * rowHeight;
            int button = this.draft.get(actions[i].option());
            boolean down = button >= 0 && reading.button(button);
            graphics.fill(x, y + 7, x + 6, y + 13, down ? this.theme.good() : BAR_BACKGROUND);
            graphics.drawString(font, Component.translatable("mcheli.joystick.action." + actions[i].translationName()),
                    x + 10, y + 6, this.theme.text());
        }
        StringBuilder pressed = new StringBuilder();
        for (int i = 0; i < reading.buttons().length; i++) {
            if (reading.buttons()[i]) {
                pressed.append(pressed.isEmpty() ? "" : " ").append(i);
            }
        }
        graphics.drawString(font, Component.translatable("mcheli.joystick.pressed", pressed.toString()), pressedX,
                pressedY, this.theme.muted());
    }

    /** The capture prompt over the bottom of the panel. */
    void prompt(GuiGraphics graphics, Font font, JoystickDraft.Waiting waiting, int left, int y) {
        Component text = waiting.axis() != null
                ? Component.translatable("mcheli.joystick.prompt.axis",
                Component.translatable("mcheli.joystick.slot." + waiting.axis().translationName()))
                : Component.translatable("mcheli.joystick.prompt.button",
                Component.translatable("mcheli.joystick.action." + waiting.action().translationName()));
        graphics.fill(left, y - 3, left + JoystickScreen.PANEL_WIDTH, y + 11, 0xE0303030);
        graphics.drawCenteredString(font, text, left + JoystickScreen.PANEL_WIDTH / 2, y, this.theme.title());
    }
}
