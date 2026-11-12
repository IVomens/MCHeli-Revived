package com.ivomens.mcheli.hud.script;

import com.ivomens.mcheli.core.content.def.hud.GraduationType;
import com.ivomens.mcheli.core.hud.HudTextValue;
import com.ivomens.mcheli.core.hud.expr.Formula;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One instruction of a compiled {@link HudScript}. Formulas are compiled; coordinates are offsets from the
 * screen centre in the HUD's design units. Every instruction keeps the line of the command it came from, for
 * messages; 0 when the file has none.
 */
public sealed interface HudInstruction {

    int line();

    /** Two formulas that go together: a position or a size. */
    record Point(Formula x, Formula y) {
    }

    /**
     * Format 1 {@code if}: sets the gate, closed when the condition is 0. Commands behind a closed gate are
     * skipped; a gate is evaluated even behind a closed one.
     */
    record Gate(int line, Formula condition) implements HudInstruction {
    }

    /** Format 1 {@code endif}: opens the gate, whatever opened or closed it. */
    record EndGate(int line) implements HudInstruction {
    }

    /** Format 2: carries on at {@code target} when the condition is 0. */
    record Branch(int line, Formula condition, int target) implements HudInstruction {
    }

    /** Format 2: carries on at {@code target}. */
    record Jump(int line, int target) implements HudInstruction {
    }

    /** Stops this script for the frame; a script that called it carries on. */
    record Exit(int line) implements HudInstruction {
    }

    /** Runs the script of another HUD in place. */
    record Call(int line, String hud) implements HudInstruction {
    }

    /** Sets the colour of what is drawn after it, which carries over into the next frame. */
    record SetColor(int line, Formula argb) implements HudInstruction {
    }

    /**
     * Text made with {@link String#format}.
     *
     * @param formatProblem why the format does not fit the values, found when the HUD loaded; the text is
     *                      then never drawn. Null when it fits.
     */
    record DrawText(int line, Point position, String format, List<HudTextValue> values, boolean centered,
                    @Nullable String formatProblem) implements HudInstruction {

        public DrawText {
            values = List.copyOf(values);
        }

        /** Whether the text is hidden while no weapon is selected. */
        public boolean needsWeapon() {
            for (int i = 0; i < this.values.size(); i++) {
                if (this.values.get(i).needsWeapon()) return true;
            }
            return false;
        }

        /** Whether the text is hidden while the selected weapon has no magazine. */
        public boolean needsMagazine() {
            for (int i = 0; i < this.values.size(); i++) {
                if (this.values.get(i).needsMagazine()) return true;
            }
            return false;
        }
    }

    /** A filled rectangle; the size is cut to whole pixels. */
    record FillRect(int line, Point position, Point size) implements HudInstruction {
    }

    /** A line through the points. */
    record DrawLine(int line, List<Point> points) implements HudInstruction {

        public DrawLine {
            points = List.copyOf(points);
        }
    }

    /** Dashed segments between pairs of points, with an OpenGL stipple {@code pattern} repeated {@code factor} times. */
    record DrawDashedLines(int line, Formula pattern, Formula factor, List<Point> points) implements HudInstruction {

        public DrawDashedLines {
            points = List.copyOf(points);
        }
    }

    /**
     * A region of {@code textures/gui/<name>.png}, rotated about its centre.
     *
     * @param texture the texture, or null when the name is no valid resource name; it then fails when drawn
     */
    record DrawTexture(int line, String name, @Nullable ResourceLocation texture, Point position, Point size,
                       Point uvPosition, Point uvSize, Formula rotation) implements HudInstruction {
    }

    /** A radar scope with a dot for every contact, or for every enemy. */
    record DrawRadar(int line, boolean allContacts, Point position, Point size, Formula rotation)
            implements HudInstruction {
    }

    /** A moving scale, rolled about its position; a missing type draws nothing. */
    record DrawGraduation(int line, @Nullable GraduationType type, Point position, Formula rotation, Formula roll)
            implements HudInstruction {
    }

    /** The gunner camera indicator: a frame with a small box where the camera points. */
    record DrawCameraIndicator(int line, Point position) implements HudInstruction {
    }
}
