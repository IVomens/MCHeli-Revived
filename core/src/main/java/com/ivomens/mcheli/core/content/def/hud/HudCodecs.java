package com.ivomens.mcheli.core.content.def.hud;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Value spellings of HUD commands. Formulas are kept as text exactly as written; a YAML number is kept as
 * the text SnakeYAML gives for it. Positions and sizes are written as {@code [x, y]}.
 */
final class HudCodecs {

    /** A formula: text, or a plain number. */
    static final ValueCodec<String> FORMULA = ValueCodec.of(HudCodecs::formula, text -> text, value -> value)
            .shaped(formulaShape());

    /** Text kept exactly as written, blanks included, such as a format string or a texture name. */
    static final ValueCodec<String> TEXT = ValueCodec.of(Codecs::text, text -> text, value -> value)
            .shaped(ValueShape.text());

    /** A TXT {@code DrawString} text, which the line writes in double quotes. */
    static final ValueCodec<String> QUOTED_TEXT = ValueCodec.of(Codecs::text,
            text -> text.substring(1, text.length() - 1), value -> value).shaped(ValueShape.text());

    /** Names, such as the variables of a {@code DrawString}: a YAML list, or the rest of a TXT line. */
    static final ValueCodec<List<String>> NAMES = ValueCodec.of(
            raw -> Codecs.list(raw).stream().map(HudCodecs::formula).toList(),
            text -> List.of(Codecs.splitTxt(text)),
            InlineList::new).shaped(ValueShape.listOf(formulaShape()));

    /** One ARGB formula, or a list of four: alpha, red, green and blue. */
    static final ValueCodec<List<String>> ARGB = ValueCodec.of(
            HudCodecs::argbFromYaml,
            text -> List.of(Codecs.splitTxt(text)),
            argb -> argb.size() == 1 ? argb.getFirst() : new InlineList(argb))
            .shaped(ValueShape.either(formulaShape(), ValueShape.tuple(4, formulaShape()))
                    .note("One ARGB formula such as '0xFF00FF00', or a list of four: [a, r, g, b]."));

    /** A position: {@code [x, y]} or {@code {x: .., y: ..}}. */
    static final ValueCodec<HudPoint> POSITION = point("x", "y");

    /** A size: {@code [width, height]} or {@code {width: .., height: ..}}. */
    static final ValueCodec<HudPoint> SIZE = point("width", "height");

    /** The points of a line: a YAML list of {@code [x, y]}, or the rest of a TXT line, two values a point. */
    static final ValueCodec<List<HudPoint>> POINTS = ValueCodec.of(
            HudCodecs::pointsFromYaml,
            HudCodecs::pointsFromText,
            points -> points.stream().map(point -> (Object) inline(point)).toList())
            .shaped(ValueShape.listOf(ValueShape.tuple(2, formulaShape())).note("A list of points [x, y]."));

    /** A graduation type by name, in any case. */
    static final ValueCodec<GraduationType> GRADUATION_TYPE = ValueCodec.of(
            raw -> graduationType(Codecs.text(raw)),
            HudCodecs::graduationType,
            Enum::name).shaped(ValueShape.words(Arrays.stream(GraduationType.values()).map(Enum::name).toList()));

    private HudCodecs() {
    }

    /** A formula over the HUD variables, or a plain number. */
    private static ValueShape formulaShape() {
        return ValueShape.either(ValueShape.text(), ValueShape.number());
    }

    private static String formula(Object raw) {
        if (raw instanceof Number number) {
            return number.toString();
        }
        return Codecs.require(raw, String.class, "a formula");
    }

    private static List<String> argbFromYaml(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of(formula(raw));
        }
        if (list.size() != 4) {
            throw new ContentValueException("a colour is one ARGB formula or a list of four (alpha, red, green, "
                    + "blue), but the list has " + list.size());
        }
        return list.stream().map(HudCodecs::formula).toList();
    }

    private static ValueCodec<HudPoint> point(String first, String second) {
        return ValueCodec.of(
                raw -> pointFromYaml(raw, first, second),
                text -> pointFromText(Codecs.splitTxt(text), 0),
                HudCodecs::inline).shaped(ValueShape.either(ValueShape.tuple(2, formulaShape()),
                        ValueShape.mapOf(formulaShape())).note("[" + first + ", " + second + "] or a map with the "
                        + "keys '" + first + "' and '" + second + "'; each a formula."));
    }

    private static HudPoint pointFromYaml(Object raw, String first, String second) {
        if (raw instanceof List<?> list) {
            if (list.size() != 2) {
                throw new ContentValueException("expected a list of two formulas but the list has " + list.size());
            }
            return new HudPoint(formula(list.get(0)), formula(list.get(1)));
        }
        if (raw instanceof Map<?, ?> map) {
            return new HudPoint(optionalFormula(map.get(first)), optionalFormula(map.get(second)));
        }
        throw new ContentValueException("expected [" + first + ", " + second + "] or a map with the keys '" + first
                + "' and '" + second + "' but got " + Codecs.describe(raw));
    }

    private static String optionalFormula(Object raw) {
        return raw == null ? null : formula(raw);
    }

    private static HudPoint pointFromText(String[] values, int index) {
        return new HudPoint(values[index], values[index + 1]);
    }

    private static List<HudPoint> pointsFromYaml(Object raw) {
        List<HudPoint> points = new ArrayList<>();
        for (Object point : Codecs.list(raw)) {
            if (!(point instanceof List<?> pair) || pair.size() != 2) {
                throw new ContentValueException("each point of a line is a list of two formulas, [x, y], but got "
                        + Codecs.describe(point));
            }
            points.add(new HudPoint(formula(pair.get(0)), formula(pair.get(1))));
        }
        return List.copyOf(points);
    }

    private static List<HudPoint> pointsFromText(String text) {
        String[] values = Codecs.splitTxt(text);
        List<HudPoint> points = new ArrayList<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            points.add(pointFromText(values, i));
        }
        return List.copyOf(points);
    }

    private static InlineList inline(HudPoint point) {
        return new InlineList(Arrays.asList(point.x(), point.y()));
    }

    private static GraduationType graduationType(String name) {
        try {
            return GraduationType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ContentValueException("unknown graduation type \"" + name + "\"; the types are "
                    + Arrays.stream(GraduationType.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }
}
