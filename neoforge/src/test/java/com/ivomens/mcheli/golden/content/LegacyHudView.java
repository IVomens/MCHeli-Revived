package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.hud.Hud;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudPoint;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.core.hud.HudTextValue;
import com.ivomens.mcheli.core.hud.expr.Formula;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A HUD as the classes before the formula compiler held it, so the content dumps and the expression golden
 * read the same from the compiled HUDs (docs/ARCHITECTURE.md Part 2, §5.1: the dump of a rewritten type comes
 * from a mapping onto the old fields).
 * <p>
 * Those classes flattened the program into a list of elements, one class per command, with an {@code if} and
 * an {@code endif} around every block, and kept every formula as the text Aviator was given: normalised,
 * then with the legacy boolean comparisons rewritten by regular expressions. This view rebuilds that list
 * from the HUD's program, field by field.
 */
public final class LegacyHudView {

    private static final String ELEMENTS = "com.ivomens.mcheli.hud.HudItem";
    private static final String EXPRESSION = "com.ivomens.mcheli.hud.HudExpression";
    private static final List<String> BOOLEAN_VARIABLES = List.of(
            "reloading", "is_heat_wpn", "have_radar", "dsp_mt_dist", "can_flare", "have_flare", "gunner_mode",
            "test_mode", "is_uav");
    private static final List<Rewrite> REWRITES = BOOLEAN_VARIABLES.stream()
            .flatMap(name -> Stream.of(
                    new Rewrite(name, "==\\s*0", "!" + name),
                    new Rewrite(name, "!=\\s*0", name),
                    new Rewrite(name, "==\\s*1", name),
                    new Rewrite(name, "!=\\s*1", "!" + name)))
            .toList();
    /** Sizes of the drawing buffers the element classes allocated up front. */
    private static final int GRADUATION_TICKS = 144;
    private static final int BOX = 8;
    private static final int CROSSHAIR = 16;

    /**
     * A formula of the old elements: the text Aviator evaluated, and the text the compiler reads for it. They
     * differ where the legacy boolean comparisons were rewritten as text.
     */
    public record LegacyFormula(String aviatorText, String compilerText) {
    }

    private final BiConsumer<String, String> lines;
    private final List<LegacyFormula> formulas;

    private LegacyHudView(BiConsumer<String, String> lines, List<LegacyFormula> formulas) {
        this.lines = lines;
        this.formulas = formulas;
    }

    /** Writes the fields the old {@code Hud} had beyond its file's info, as {@code path = value} dump lines. */
    static void writeFields(ContentEntry<HudProgram> hud, String prefix, BiConsumer<String, String> lines) {
        for (String flag : List.of("broken", "exit", "isDrawing", "isIfFalse", "isWaitEndif")) {
            lines.accept(prefix + flag, "false");
        }
        new LegacyHudView(lines, new ArrayList<>()).writeElements(hud, prefix + "list");
    }

    /** Every formula of the old elements of {@code hud}, in element order. */
    public static List<LegacyFormula> formulas(ContentEntry<HudProgram> hud) {
        List<LegacyFormula> formulas = new ArrayList<>();
        new LegacyHudView((path, value) -> {
        }, formulas).writeElements(hud, "list");
        return formulas;
    }

    /** The Aviator text of a normalised formula: the legacy boolean comparisons rewritten as text. */
    public static String aviatorText(String normalised) {
        String formula = normalised;
        for (Rewrite rewrite : REWRITES) {
            if (formula.contains(rewrite.variable)) {
                formula = rewrite.pattern.matcher(formula).replaceAll(rewrite.replacement);
            }
        }
        return formula;
    }

    private void writeElements(ContentEntry<HudProgram> hud, String path) {
        List<Runnable> elements = new ArrayList<>();
        collect(hud.definition().ops(), hud.isLegacyTxt(), path, elements);
        this.lines.accept(path + ".size", Integer.toString(elements.size()));
        elements.forEach(Runnable::run);
    }

    /** Queues each element's lines; an {@code If} becomes an {@code if} element, its body, an {@code endif}. */
    private void collect(List<HudOp> ops, boolean legacyTxt, String path, List<Runnable> elements) {
        for (HudOp op : ops) {
            if (op instanceof HudOp.If block) {
                String ifPath = element(path, elements.size());
                elements.add(() -> conditional(ifPath, legacyTxt ? block.line() : 0, false, block.condition()));
                collect(block.body(), legacyTxt, path, elements);
                String endPath = element(path, elements.size());
                elements.add(() -> conditional(endPath, legacyTxt ? block.endLine() : 0, true, legacyTxt ? "" : null));
            } else {
                String elementPath = element(path, elements.size());
                elements.add(() -> element(elementPath, op, legacyTxt));
            }
        }
    }

    private static String element(String path, int index) {
        return path + "[" + index + "]";
    }

    /** The old elements kept no line for YAML commands, which had none to keep. */
    private void element(String path, HudOp op, boolean legacyTxt) {
        int line = legacyTxt ? op.line() : 0;
        switch (op) {
            case HudOp.Exit exit -> header(path, "Exit", line);
            case HudOp.Call call -> {
                header(path, "Call", line);
                text(path + ".hudName", call.hud());
            }
            case HudOp.Color color -> {
                header(path, "Color", line);
                color(path + ".color", color.argb());
            }
            case HudOp.DrawString text -> drawString(path, line, text);
            case HudOp.DrawRect rect -> {
                header(path, "Rect", line);
                expressions(path, List.of("left", "top", "width", "height"), x(rect.position()), y(rect.position()),
                        x(rect.size()), y(rect.size()));
            }
            case HudOp.DrawLine drawn -> drawLine(path, line, drawn);
            case HudOp.DrawTexture texture -> drawTexture(path, line, texture);
            case HudOp.DrawRadar radar -> {
                header(path, "Radar", line);
                expressions(path, List.of("rot", "left", "top", "width", "height"), radar.rotation(),
                        x(radar.position()), y(radar.position()), x(radar.size()), y(radar.size()));
                this.lines.accept(path + ".isEntityRadar", Boolean.toString(radar.entityRadar()));
                this.lines.accept(path + ".buffer.size", "0");
            }
            case HudOp.DrawGraduation scale -> {
                header(path, "Graduation", line);
                expressions(path, List.of("rot", "roll", "posX", "posY"), scale.rotation(), scale.roll(),
                        x(scale.position()), y(scale.position()));
                this.lines.accept(path + ".type", Integer.toString(scale.type() == null ? -1 : scale.type().ordinal()));
                zeros(path + ".lineBuffer", BOX);
                zeros(path + ".ticks", GRADUATION_TICKS);
                zeros(path + ".bracket", BOX);
            }
            case HudOp.CameraRotation camera -> {
                header(path, "CameraRot", line);
                expressions(path, List.of("posX", "posY"), x(camera.position()), y(camera.position()));
                zeros(path + ".frame", BOX);
                zeros(path + ".crosshair", CROSSHAIR);
                zeros(path + ".marker", BOX);
            }
            case HudOp.If block -> throw new IllegalArgumentException("an If block is not one element");
        }
    }

    private void conditional(String path, int line, boolean endif, String condition) {
        header(path, "Conditional", line);
        this.lines.accept(path + ".isEndif", Boolean.toString(endif));
        expression(path + ".condition", condition);
    }

    private void drawString(String path, int line, HudOp.DrawString text) {
        header(path, "String", line);
        expressions(path, List.of("posX", "posY"), x(text.position()), y(text.position()));
        text(path + ".format", text.format());
        List<HudTextValue> values = text.variables().stream()
                .map(name -> HudTextValue.named(name).orElse(HudTextValue.NONE)).toList();
        this.lines.accept(path + ".args.size", Integer.toString(values.size()));
        this.lines.accept(path + ".values.size", Integer.toString(values.size()));
        for (int i = 0; i < values.size(); i++) {
            this.lines.accept(path + ".args[" + i + "]", "HudItemStringArgs." + values.get(i).name());
            this.lines.accept(path + ".values[" + i + "]", "null");
        }
        this.lines.accept(path + ".isCenteredString", Boolean.toString(text.centered()));
        this.lines.accept(path + ".needsWeapon", Boolean.toString(values.stream().anyMatch(HudTextValue::needsWeapon)));
        this.lines.accept(path + ".needsMagazine",
                Boolean.toString(values.stream().anyMatch(HudTextValue::needsMagazine)));
    }

    private void drawLine(String path, int line, HudOp.DrawLine drawn) {
        List<String> coordinates = drawn.points().stream().flatMap(point -> Stream.of(point.x(), point.y())).toList();
        header(path, drawn.striped() ? "LineStipple" : "Line", line);
        if (drawn.striped()) {
            expression(path + ".pattern", drawn.pattern());
            expression(path + ".factor", drawn.factor());
        }
        this.lines.accept(path + ".coordinates.size", Integer.toString(coordinates.size()));
        for (int i = 0; i < coordinates.size(); i++) {
            expression(path + ".coordinates[" + i + "]", coordinates.get(i));
        }
        zeros(path + ".points", coordinates.size() & ~1);
    }

    private void drawTexture(String path, int line, HudOp.DrawTexture texture) {
        header(path, "Texture", line);
        text(path + ".name", texture.texture());
        expressions(path, List.of("left", "top", "width", "height", "uLeft", "vTop", "uWidth", "vHeight", "rot"),
                x(texture.position()), y(texture.position()), x(texture.size()), y(texture.size()),
                x(texture.uvPosition()), y(texture.uvPosition()), x(texture.uvSize()), y(texture.uvSize()),
                texture.rotation());
        this.lines.accept(path + ".texture", "null");
        this.lines.accept(path + ".textureWidth", "0");
        this.lines.accept(path + ".textureHeight", "0");
    }

    private void header(String path, String element, int line) {
        this.lines.accept(path + ".@class", ELEMENTS + element);
        this.lines.accept(path + ".fileLine", Integer.toString(line));
        this.lines.accept(path + ".parent", "<cycle Hud>");
    }

    /** One ARGB formula, or four components combined the way the old colour element combined them. */
    private void color(String path, List<String> argb) {
        if (argb.size() == 1) {
            expression(path, argb.getFirst());
            return;
        }
        List<String> normalised = argb.stream().map(Formula::normalise).toList();
        String spelling = "((%s)<<24)|((%s)<<16)|((%s)<<8 )|((%s)<<0 )";
        String aviatorText = spelling.formatted(normalised.stream().map(LegacyHudView::aviatorText).toArray());
        expression(path, aviatorText, spelling.formatted(normalised.toArray()));
    }

    private void expressions(String path, List<String> names, String... formulas) {
        for (int i = 0; i < names.size(); i++) {
            expression(path + "." + names.get(i), formulas[i]);
        }
    }

    private void expression(String path, String formula) {
        String normalised = Formula.normalise(formula);
        expression(path, aviatorText(normalised), normalised);
    }

    private void expression(String path, String aviatorText, String compilerText) {
        this.formulas.add(new LegacyFormula(aviatorText, compilerText));
        this.lines.accept(path + ".@class", EXPRESSION);
        this.lines.accept(path + ".compiled", "null");
        text(path + ".source", aviatorText);
    }

    private void zeros(String path, int count) {
        this.lines.accept(path + ".size", Integer.toString(count));
        for (int i = 0; i < count; i++) {
            this.lines.accept(path + "[" + i + "]", "0.0");
        }
    }

    private void text(String path, String value) {
        this.lines.accept(path, value == null ? "null" : InfoDump.quote(value));
    }

    private static String x(HudPoint point) {
        return point == null ? null : point.x();
    }

    private static String y(HudPoint point) {
        return point == null ? null : point.y();
    }

    private record Rewrite(String variable, Pattern pattern, String replacement) {

        Rewrite(String variable, String comparison, String replacement) {
            this(variable, Pattern.compile("\\b" + variable + "\\s*" + comparison + "\\b"),
                    Matcher.quoteReplacement(replacement));
        }
    }
}
