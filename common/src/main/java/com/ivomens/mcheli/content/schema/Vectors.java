package com.ivomens.mcheli.content.schema;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Positions and directions: YAML {@code [x, y, z]}, legacy TXT {@code x, y, z} (three values of a line).
 * <p>
 * The legacy parser read some TXT vectors as doubles and others as floats, and stored some positions
 * raised by a fixed offset; the TXT spellings below keep each of those exactly, so a TXT value turns into
 * the YAML value that loads the same.
 */
public final class Vectors {

    /** A vector of doubles; YAML components may be written as numbers or numeric text. */
    public static final ValueCodec<Vec3> VEC3 = ValueCodec.of(
            raw -> fromYaml(raw, false),
            text -> fromText(text, false),
            Vectors::toYaml).shaped(shape());

    /** The TXT spelling of {@link #VEC3} for the lines the legacy parser read with float precision. */
    public static final ValueCodec<Vec3> TXT_FLOATS = ValueCodec.of(
            raw -> fromYaml(raw, false),
            text -> fromText(text, true),
            Vectors::toYaml).shaped(shape());

    /**
     * A vector the game keeps with float precision, such as a weapon mount, in both formats; the emitter writes
     * the floats, so a converted file shows {@code 0.2} rather than its double {@code 0.20000000298023224}.
     */
    public static final ValueCodec<Vec3> FLOAT_VEC3 = ValueCodec.of(
            raw -> fromYaml(raw, true),
            text -> fromText(text, true),
            Vectors::toFloatYaml).shaped(shape());

    private Vectors() {
    }

    /** {@code [x, y, z]}; a component may be numeric text. */
    public static ValueShape shape() {
        return ValueShape.tuple(3, numberOrNumericText()).note("A list [x, y, z] of three numbers.");
    }

    /** A number, or text that reads as one. */
    public static ValueShape numberOrNumericText() {
        return ValueShape.either(ValueShape.number(),
                ValueShape.text("^\\s*[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?[fFdD]?\\s*$"));
    }

    private static Vec3 fromYaml(Object raw, boolean floats) {
        List<?> list = Codecs.require(raw, List.class, "a list [x, y, z]");
        if (list.size() != 3) {
            throw new ContentValueException("expected a list [x, y, z] of three numbers but got " + list.size()
                    + (list.size() == 1 ? " value" : " values"));
        }
        double x = component(list.get(0));
        double y = component(list.get(1));
        double z = component(list.get(2));
        return floats ? new Vec3((float) x, (float) y, (float) z) : new Vec3(x, y, z);
    }

    private static double component(Object raw) {
        if (raw instanceof String text) {
            return Codecs.parseDouble(text.trim());
        }
        return Codecs.number(raw).doubleValue();
    }

    private static Vec3 fromText(String text, boolean floats) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length != 3) {
            throw new ContentValueException("expected three values x, y, z but got \"" + text + "\"");
        }
        if (floats) {
            return new Vec3(Codecs.parseFloat(parts[0]), Codecs.parseFloat(parts[1]), Codecs.parseFloat(parts[2]));
        }
        return new Vec3(Codecs.parseDouble(parts[0]), Codecs.parseDouble(parts[1]), Codecs.parseDouble(parts[2]));
    }

    private static Object toYaml(Vec3 vector) {
        return new InlineList(List.of(vector.x, vector.y, vector.z));
    }

    private static Object toFloatYaml(Vec3 vector) {
        return new InlineList(List.of((float) vector.x, (float) vector.y, (float) vector.z));
    }
}
