package com.ivomens.mcheli.hud.script;

import com.ivomens.mcheli.core.content.def.hud.HudCommands;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudPoint;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.core.hud.HudTextValue;
import com.ivomens.mcheli.core.hud.HudVariable;
import com.ivomens.mcheli.core.hud.expr.Formula;
import com.ivomens.mcheli.hud.script.HudInstruction.Point;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;

/**
 * Compiles a HUD program into a {@link HudScript}: every formula against the {@link HudVariable}s, every
 * command into its instruction, and the {@code If} blocks into gates (the first format) or jumps (format 2).
 * <p>
 * Whatever cannot work is reported now, once, with the file, the line and the command, instead of while
 * drawing: formulas that cannot be read or name no variable, {@code DrawString} values that do not exist or do
 * not fit the format, texture names that are no resource name. The HUD still loads, and draws as it always
 * did; a {@code DrawString} whose format does not fit is the one command that is now skipped instead of
 * stopping the HUD.
 */
public final class HudScriptCompiler {

    private final String file;
    private final List<HudInstruction> code = new ArrayList<>();

    private HudScriptCompiler(String file) {
        this.file = file;
    }

    /** @param file the path of the program's file inside its pack, for messages */
    public static HudScript compile(HudProgram program, String file) {
        HudScriptCompiler compiler = new HudScriptCompiler(file);
        if (program.nestsBlocks()) {
            compiler.lowerToJumps(program.ops());
        } else {
            compiler.lowerToGates(program.ops());
        }
        return new HudScript(file, program.ignoreAutoScale(), compiler.code);
    }

    /**
     * The first format: an {@code If} becomes a gate around its body, the gate, the commands, then an end.
     * Its blocks cannot nest, so a gate is all an {@code If} needs; {@link HudInstruction.Gate} keeps the
     * gate's own rules for the scripts that always ran on them.
     */
    private void lowerToGates(List<HudOp> ops) {
        for (HudOp op : ops) {
            if (op instanceof HudOp.If block) {
                this.code.add(new HudInstruction.Gate(block.line(), formula(block, block.condition())));
                lowerToGates(block.body());
                this.code.add(new HudInstruction.EndGate(block.endLine()));
            } else {
                this.code.add(instruction(op));
            }
        }
    }

    /**
     * Format 2: an {@code If} becomes a branch over its body to its {@code Else}, and a jump from the end of
     * the body past the {@code Else}. Blocks nest, and a condition is only evaluated when it is reached.
     */
    private void lowerToJumps(List<HudOp> ops) {
        for (HudOp op : ops) {
            if (!(op instanceof HudOp.If block)) {
                this.code.add(instruction(op));
                continue;
            }
            Formula condition = formula(block, block.condition());
            int branch = reserve();
            lowerToJumps(block.body());
            if (block.otherwise().isEmpty()) {
                this.code.set(branch, new HudInstruction.Branch(block.line(), condition, this.code.size()));
                continue;
            }
            int jump = reserve();
            this.code.set(branch, new HudInstruction.Branch(block.line(), condition, this.code.size()));
            lowerToJumps(block.otherwise());
            this.code.set(jump, new HudInstruction.Jump(block.line(), this.code.size()));
        }
    }

    /** A place for an instruction whose target is not known yet. */
    private int reserve() {
        this.code.add(null);
        return this.code.size() - 1;
    }

    private HudInstruction instruction(HudOp op) {
        int line = op.line();
        return switch (op) {
            case HudOp.Exit exit -> new HudInstruction.Exit(line);
            case HudOp.Call call -> new HudInstruction.Call(line, call.hud());
            case HudOp.Color color -> new HudInstruction.SetColor(line, color(color));
            case HudOp.DrawString text -> text(text);
            case HudOp.DrawRect rect -> new HudInstruction.FillRect(line, point(op, rect.position()),
                    point(op, rect.size()));
            case HudOp.DrawLine drawn -> drawn.striped()
                    ? new HudInstruction.DrawDashedLines(line, formula(op, drawn.pattern()),
                    formula(op, drawn.factor()), points(op, drawn.points()))
                    : new HudInstruction.DrawLine(line, points(op, drawn.points()));
            case HudOp.DrawTexture texture -> new HudInstruction.DrawTexture(line, texture.texture(),
                    textureLocation(texture), point(op, texture.position()), point(op, texture.size()),
                    point(op, texture.uvPosition()), point(op, texture.uvSize()), formula(op, texture.rotation()));
            case HudOp.DrawRadar radar -> new HudInstruction.DrawRadar(line, radar.entityRadar(),
                    point(op, radar.position()), point(op, radar.size()), formula(op, radar.rotation()));
            case HudOp.DrawGraduation scale -> new HudInstruction.DrawGraduation(line, scale.type(),
                    point(op, scale.position()), formula(op, scale.rotation()), formula(op, scale.roll()));
            case HudOp.CameraRotation camera -> new HudInstruction.DrawCameraIndicator(line,
                    point(op, camera.position()));
            case HudOp.If block -> throw new IllegalArgumentException("an If block is lowered, not compiled");
        };
    }

    /**
     * One ARGB formula, or four components combined as {@code (a<<24)|(r<<16)|(g<<8)|(b<<0)}. The shifts stay
     * even for blue: a component that is no integer fails the colour, as it always did.
     */
    private Formula color(HudOp.Color color) {
        List<String> argb = color.argb();
        if (argb.size() == 1) {
            return formula(color, argb.getFirst());
        }
        String combined = "((" + Formula.normalise(argb.get(0)) + ")<<24)|((" + Formula.normalise(argb.get(1))
                + ")<<16)|((" + Formula.normalise(argb.get(2)) + ")<<8)|((" + Formula.normalise(argb.get(3))
                + ")<<0)";
        return Formula.compile(combined, HudVariable.TABLE, problem -> report(color, problem));
    }

    private HudInstruction text(HudOp.DrawString text) {
        List<HudTextValue> values = new ArrayList<>();
        for (String name : text.variables()) {
            values.add(HudTextValue.named(name).orElseGet(() -> {
                report(text, "unknown HUD string variable '" + name + "', which shows as null");
                return HudTextValue.NONE;
            }));
        }
        String formatProblem = formatProblem(text.format(), values);
        if (formatProblem != null) {
            report(text, "the format \"" + text.format() + "\" does not fit its values (" + formatProblem
                    + "), so the text is not drawn");
        }
        return new HudInstruction.DrawText(text.line(), point(text, text.position()), text.format(), values,
                text.centered(), formatProblem);
    }

    /** Why {@link String#format} rejects the format for values of these types, or null when it takes them. */
    private static String formatProblem(String format, List<HudTextValue> values) {
        try {
            String.format(Locale.ROOT, format, values.stream().map(HudTextValue::sample).toArray());
            return null;
        } catch (IllegalFormatException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private ResourceLocation textureLocation(HudOp.DrawTexture texture) {
        ResourceLocation location = ResourceLocation.tryBuild(Ids.MOD_ID, "textures/gui/" + texture.texture() + ".png");
        if (location == null) {
            report(texture, "'" + texture.texture() + "' is not a valid texture name, so the HUD stops here");
        }
        return location;
    }

    private List<Point> points(HudOp op, List<HudPoint> points) {
        return points.stream().map(point -> point(op, point)).toList();
    }

    /** A point; a point or a coordinate the file leaves out is 0. */
    private Point point(HudOp op, HudPoint point) {
        return point == null ? new Point(formula(op, null), formula(op, null))
                : new Point(formula(op, point.x()), formula(op, point.y()));
    }

    private Formula formula(HudOp op, String text) {
        return Formula.compile(Formula.normalise(text), HudVariable.TABLE, problem -> report(op, problem));
    }

    private void report(HudOp op, String message) {
        String command = op instanceof HudOp.If ? "If" : HudCommands.of(op).key();
        String where = op.line() > 0 ? this.file + ":" + op.line() : this.file;
        ModLogger.get().warn("{} {}: {}", where, command, message);
    }
}
