package com.ivomens.mcheli.core.content.def.hud;

import java.util.List;

/**
 * One command of a {@link HudProgram}. Formulas are the file's own text; coordinates are offsets from the
 * screen centre. {@link HudCommands} declares how each command is spelled in YAML and TXT.
 */
public sealed interface HudOp {

    /** The TXT line the command is on, for messages while drawing; 0 in YAML, which has no line numbers. */
    int line();

    /**
     * Commands drawn only while {@code condition} is not 0, and in {@link HudProgram#FORMAT_NESTED format 2}
     * others drawn when it is 0. In the first format blocks do not nest: in TXT a second {@code if} before
     * {@code endif} fails the file, and YAML does not read an {@code If} inside {@code Do}. In format 2 the
     * bodies may hold blocks of their own.
     *
     * @param otherwise the {@code Else} commands; always empty in the first format
     * @param endLine   the TXT line of the {@code endif}
     */
    record If(int line, String condition, List<HudOp> body, List<HudOp> otherwise, int endLine) implements HudOp {

        public If {
            body = List.copyOf(body);
            otherwise = List.copyOf(otherwise);
        }

        /** A block without {@code Else}. */
        public If(int line, String condition, List<HudOp> body, int endLine) {
            this(line, condition, body, List.of(), endLine);
        }
    }

    /** Stops drawing this HUD for the frame; a HUD that called it carries on. */
    record Exit(int line) implements HudOp {
    }

    /** Draws another HUD in place. */
    record Call(int line, String hud) implements HudOp {
    }

    /**
     * Sets the colour of everything drawn after it, which stays set into the next frame.
     *
     * @param argb one formula for the whole ARGB value, or four for alpha, red, green and blue
     */
    record Color(int line, List<String> argb) implements HudOp {

        public Color {
            argb = List.copyOf(argb);
        }
    }

    /**
     * Text made with {@link String#format}; the variables are HUD string values by name, and the
     * conversion in {@code format} must suit each value's type.
     */
    record DrawString(int line, HudPoint position, String format, List<String> variables, boolean centered)
            implements HudOp {

        public DrawString {
            variables = List.copyOf(variables);
        }
    }

    /** A filled rectangle; a missing size is 0. */
    record DrawRect(int line, HudPoint position, HudPoint size) implements HudOp {
    }

    /**
     * A line through {@code points}; a striped line is drawn as dashed segments between pairs of points,
     * with an OpenGL stipple {@code pattern} repeated {@code factor} times.
     */
    record DrawLine(int line, List<HudPoint> points, boolean striped, String pattern, String factor)
            implements HudOp {

        public DrawLine {
            points = List.copyOf(points);
        }
    }

    /** A region of {@code textures/gui/<texture>.png}, rotated about its centre; missing sizes are 0. */
    record DrawTexture(int line, String texture, HudPoint position, HudPoint size, HudPoint uvPosition,
                       HudPoint uvSize, String rotation) implements HudOp {
    }

    /** A radar scope with dots for every radar contact, or for enemies only. */
    record DrawRadar(int line, boolean entityRadar, HudPoint position, HudPoint size, String rotation)
            implements HudOp {
    }

    /** A moving scale; a missing type draws nothing. */
    record DrawGraduation(int line, GraduationType type, HudPoint position, String rotation, String roll)
            implements HudOp {
    }

    /** The gunner camera indicator: a frame with a small box showing where the camera points. */
    record CameraRotation(int line, HudPoint position) implements HudOp {
    }
}
