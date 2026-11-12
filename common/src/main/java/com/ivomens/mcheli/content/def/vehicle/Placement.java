package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The keys every drawn part of {@code Components} starts with, declared once per part type:
 * {@code Position} (or {@code Pos}), {@code Rotation} (or {@code Rot}) and {@code PartName}. A part without a
 * name is named after its type and its place in the list: {@code hatch0}, {@code hatch1}, ...
 * <p>
 * Legacy TXT lines read positions and rotations as floats, and always name their parts. The emitter always
 * writes the rotation, so a converted file shows each part's axis. The emitter always
 * writes the rotation, so a converted file shows each part's axis.
 *
 * @param prefix the start of the names of unnamed parts
 * @param <P>    the part record
 */
record Placement<P>(Field<P, Vec3> position, Field<P, Vec3> rotation, Field<P, String> name, String prefix) {

    /** A part name, as written. */
    private static final ValueCodec<String> PART_NAME = ValueCodec.of(
            raw -> Codecs.require(raw, String.class, "a part name"),
            text -> text,
            name -> name).shaped(ValueShape.text());
    /** Wheels keep their names in lower case. */
    private static final ValueCodec<String> LOWER_CASE_NAME = ValueCodec.of(
            raw -> Codecs.require(raw, String.class, "a part name").toLowerCase(Locale.ROOT).trim(),
            text -> text,
            name -> name).shaped(ValueShape.text());
    /** Wheels turn about the y axis unless they say otherwise. */
    private static final Vec3 WHEEL_AXIS = new Vec3(0.0, 1.0, 0.0);

    static <P> Placement<P> of(String prefix, Function<P, Vec3> position, Function<P, Vec3> rotation,
                               Function<P, String> name) {
        return new Placement<>(
                Field.of("Position", Vectors.VEC3, null, position).alias("Pos").txt(Vectors.TXT_FLOATS),
                Field.of("Rotation", Vectors.VEC3, Vec3.ZERO, rotation).alias("Rot").txt(Vectors.TXT_FLOATS)
                        .alwaysWritten(),
                Field.of("PartName", PART_NAME, null, name), prefix);
    }

    /** The keys of a wheel: no short spellings, an upright axis by default, lower-case names. */
    static <P> Placement<P> ofWheel(String prefix, Function<P, Vec3> position, Function<P, Vec3> rotation,
                                    Function<P, String> name) {
        return new Placement<>(Field.of("Position", Vectors.VEC3, null, position).txt(Vectors.TXT_FLOATS),
                Field.of("Rotation", Vectors.VEC3, WHEEL_AXIS, rotation).txt(Vectors.TXT_FLOATS).alwaysWritten(),
                Field.of("PartName", LOWER_CASE_NAME, null, name), prefix);
    }

    /**
     * An entry schema with these keys first, then the part type's own. It cannot do without a position, and
     * reports the {@code Type} key older converters wrote on every part.
     */
    @SafeVarargs
    final EntrySchema<P> entry(String what, Function<Values, P> build, Member<P>... more) {
        List<Member<P>> members = new ArrayList<>(List.of(this.position, this.rotation, this.name));
        members.addAll(List.of(more));
        return new EntrySchema<>(what, Section.root(members).requires(this.position).unsupported("Type"), build);
    }

    Vec3 position(Values v) {
        return v.get(this.position);
    }

    Vec3 rotation(Values v) {
        return v.get(this.rotation);
    }

    /** The part's own name, or the type's prefix and its place in the list. */
    String name(Values v) {
        String written = v.get(this.name);
        return written != null ? written : this.prefix + v.index();
    }
}
