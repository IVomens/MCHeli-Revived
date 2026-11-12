package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.core.content.def.hud.GraduationType;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.core.math.Degrees;
import net.minecraft.util.Mth;

/**
 * The drawn-in-code parts of the scripted HUDs: the moving scales of {@code DrawGraduation} and the gunner
 * camera indicator. Positions are canvas pixels; the buffers are reused from frame to frame.
 */
final class ScriptGauges {

    private static final int DASHED_STIPPLE = 0xCCCC;
    private static final int CARDINAL_LABEL_COLOR = 0xFFFF0000;
    private static final int YAW_TICKS = 19;
    private static final int PITCH_TICKS = 18;
    private static final double CAMERA_HALF_WIDTH = 21.0;
    private static final double CAMERA_HALF_HEIGHT = 11.0;
    private static final float CAMERA_MIN_PITCH = -30.0F;
    private static final float CAMERA_MAX_PITCH = 70.0F;
    private static final float CAMERA_MAX_YAW = 50.0F;

    private final double[] line = new double[8];
    private final double[] ticks = new double[Math.max(YAW_TICKS * 4, PITCH_TICKS * 8)];
    private final double[] bracket = new double[8];
    private final double[] crosshair = new double[16];

    /** One of the scales, centred on {@code (x, y)}, for {@code value} degrees of heading or pitch. */
    void graduation(HudCanvas canvas, GraduationType type, double value, int x, int y, int color, int guiScale) {
        switch (type) {
            case YAW -> yawTape(canvas, value, x, y, color);
            case PITCH -> pitchTicks(canvas, value, x, y, color);
            case PITCH_ROLL -> pitchBars(canvas, value, x, y, color, guiScale, false);
            case PITCH_ROLL_ALT -> pitchBars(canvas, value, x, y, color, guiScale, true);
        }
    }

    /** Numbered horizontal bars every 5 degrees; bars below the horizon are dashed. */
    private void pitchBars(HudCanvas canvas, double pitchValue, int centerX, int centerY, int color, int guiScale,
                           boolean wide) {
        double pitch = -pitchValue;
        int roundedPitch = (int) pitch / 5 * 5;
        double remainder = pitch % 5.0;
        int first = wide ? 1 : 0;
        int last = wide ? 4 : 5;
        int spacing = wide ? 2 : 1;
        double[] l = this.line;

        for (int i = first; i < last; i++) {
            int barPitch = roundedPitch + 10 - i * 5;
            int outerX = barPitch != 0 ? 50 : 100;
            int barY = centerY + (int) (-60 * spacing + remainder * 6.0 * spacing + i * 30 * spacing);
            int tipOffset = barPitch > 0 ? 2 : (barPitch == 0 ? 0 : -2);

            l[0] = centerX - outerX;
            l[1] = barY + tipOffset;
            l[2] = centerX - 50;
            l[3] = barY;
            l[4] = centerX + outerX;
            l[5] = barY + tipOffset;
            l[6] = centerX + 50;
            l[7] = barY;
            canvas.lines(l, l.length, LineMode.LINES, color);

            l[0] = centerX - 50;
            l[1] = barY;
            l[2] = centerX - 30;
            l[3] = barY;
            l[4] = centerX + 50;
            l[5] = barY;
            l[6] = centerX + 30;
            l[7] = barY;
            if (barPitch >= 0) {
                canvas.lines(l, l.length, LineMode.LINES, color);
            } else {
                canvas.dashedLines(l, l.length, LineMode.LINES, color, guiScale, DASHED_STIPPLE);
            }

            if (barPitch != 0) {
                String label = Integer.toString(barPitch);
                canvas.centeredText(label, centerX - 60, barY - 4, color);
                canvas.centeredText(label, centerX + 60, barY - 4, color);
            }
        }
    }

    /** Ticks every degree on both sides (long ones every 3 degrees) inside two brackets. */
    private void pitchTicks(HudCanvas canvas, double pitchValue, int centerX, int centerY, int color) {
        int pitch = (int) pitchValue % 360;
        int offset = (int) (pitchValue * 10.0 % 10.0);
        if (offset < 0) {
            offset += 10;
        }

        int leftX = centerX - 100;
        int rightX = centerX + 100;
        int topY = centerY - 80;
        int tickPitch = pitchValue < 0.0 && offset != 0 ? pitch - 9 : pitch - 8;
        double[] t = this.ticks;

        for (int i = 0; i < PITCH_TICKS; i++, tickPitch++) {
            int length = tickPitch % 3 == 0 ? 15 : 5;
            int y = topY + i * 10 - offset;
            int base = i * 8;
            t[base] = leftX - length;
            t[base + 1] = y;
            t[base + 2] = leftX;
            t[base + 3] = y;
            t[base + 4] = rightX + length;
            t[base + 5] = y;
            t[base + 6] = rightX;
            t[base + 7] = y;
        }
        canvas.lines(t, t.length, LineMode.LINES, color);

        setBracket(leftX - 25, leftX, topY);
        canvas.lines(this.bracket, this.bracket.length, LineMode.LINE_STRIP, color);
        setBracket(rightX + 25, rightX, topY);
        canvas.lines(this.bracket, this.bracket.length, LineMode.LINE_STRIP, color);
    }

    private void setBracket(int outerX, int innerX, int topY) {
        double[] b = this.bracket;
        b[0] = outerX;
        b[1] = topY - 90;
        b[2] = innerX;
        b[3] = topY - 90;
        b[4] = innerX;
        b[5] = topY + 90;
        b[6] = outerX;
        b[7] = topY + 90;
    }

    /** Compass tape: a tick per degree, labels every 3 degrees, cardinal letters every 45. */
    private void yawTape(HudCanvas canvas, double yawValue, int centerX, int centerY, int color) {
        double yaw = Degrees.wrap360(yawValue);
        int startX = centerX - 90;
        int offset = (int) (yaw * 10.0) % 10;
        int tickYaw = (int) yaw - 9;
        double[] t = this.ticks;

        for (int i = 0; i < YAW_TICKS; i++, tickYaw++) {
            int x = startX + i * 10 - offset;
            t[i * 4] = x;
            t[i * 4 + 1] = centerY;
            t[i * 4 + 2] = x;
            t[i * 4 + 3] = centerY + (tickYaw % 3 == 0 ? 10 : 5);
            if (tickYaw % 45 == 0) {
                canvas.centeredText(Degrees.compass8(tickYaw), x, centerY - 10, CARDINAL_LABEL_COLOR);
            } else if (tickYaw % 3 == 0) {
                int heading = tickYaw + 180;
                if (heading < 0) heading += 360;
                if (heading > 360) heading -= 360;
                canvas.centeredText(Integer.toString(heading), x, centerY - 10, color);
            }
        }
        canvas.lines(t, YAW_TICKS * 4, LineMode.LINES, color);
    }

    /** The camera frame at {@code (x, y)}, its dashed crosshair, and the box where the camera points. */
    void cameraIndicator(HudCanvas canvas, double x, double y, float vehicleYaw, float cameraYaw, float cameraPitch,
                         int color, int guiScale) {
        setBox(this.line, x, y, CAMERA_HALF_WIDTH, CAMERA_HALF_HEIGHT);
        canvas.lines(this.line, this.line.length, LineMode.LINE_LOOP, color);

        double[] c = this.crosshair;
        c[0] = x - CAMERA_HALF_WIDTH; c[1] = y; c[2] = x; c[3] = y;
        c[4] = x + CAMERA_HALF_WIDTH; c[5] = y; c[6] = x; c[7] = y;
        c[8] = x; c[9] = y - CAMERA_HALF_HEIGHT; c[10] = x; c[11] = y;
        c[12] = x; c[13] = y + CAMERA_HALF_HEIGHT; c[14] = x; c[15] = y;
        canvas.dashedLines(c, c.length, LineMode.LINES, color, guiScale, DASHED_STIPPLE);

        float pitch = (float) ((Mth.clamp(cameraPitch, CAMERA_MIN_PITCH, CAMERA_MAX_PITCH) - 20.0F) * 0.16);
        float yaw = (float) Degrees.difference(vehicleYaw, cameraYaw) * 2.0F;
        yaw = (float) (Mth.clamp(yaw, -CAMERA_MAX_YAW, CAMERA_MAX_YAW) * 0.34);
        setBox(this.line, x + yaw, y + pitch, 3.0, 2.0);
        canvas.lines(this.line, this.line.length, LineMode.LINE_LOOP, color);
    }

    private static void setBox(double[] box, double centerX, double centerY, double halfWidth, double halfHeight) {
        box[0] = centerX - halfWidth;
        box[1] = centerY - halfHeight;
        box[2] = centerX + halfWidth;
        box[3] = centerY - halfHeight;
        box[4] = centerX + halfWidth;
        box[5] = centerY + halfHeight;
        box[6] = centerX - halfWidth;
        box[7] = centerY + halfHeight;
    }
}
