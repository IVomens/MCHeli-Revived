package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.HitBox;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.WheelHitBox;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Wheels;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/**
 * Where the vehicle can be hit and where it touches the ground: YAML {@code BoundingBoxes} and
 * {@code Wheels}.
 * <pre>
 * BoundingBoxes:
 * - Type: DEFAULT                # DEFAULT, ENGINE or TURRET (turns with the turret)
 *   Position: [0.0, 2.5, -1.0]
 *   Size: [2.5, 3.4, 2.5]        # width, height, depth
 *   DamageFactor: 0.5
 * Wheels:
 *   Hitboxes:
 *   - Position: [1.5, -0.2, 3.0]
 *   WheelRotation: -20.0
 * </pre>
 */
final class HitBoxSchema {

    /** A box type by name; legacy TXT reads a name it does not know as {@code DEFAULT}. */
    static final ValueCodec<HitBoxType> BOX_TYPE = ValueCodec.of(
            HitBoxSchema::boxType,
            text -> Arrays.stream(HitBoxType.values())
                    .filter(type -> type.name().equals(text.toUpperCase(Locale.ROOT))).findFirst()
                    .orElse(HitBoxType.DEFAULT),
            Enum::name).shaped(ValueShape.words(Arrays.stream(HitBoxType.values()).map(Enum::name).toList()));

    static final Field<HitBox, HitBoxType> BOX_KIND =
            Field.of("Type", BOX_TYPE, HitBoxType.DEFAULT, HitBox::type).alwaysWritten();
    static final Field<HitBox, Vec3> BOX_POSITION = Field.of("Position", Vectors.VEC3, null, HitBox::position)
            .alias("Pos").txt(Vectors.TXT_FLOATS);
    /** Width, height and depth; the game keeps them as floats. */
    static final Field<HitBox, Vec3> BOX_SIZE = Field.of("Size", Vectors.FLOAT_VEC3, null, HitBox::size);
    static final Field<HitBox, Float> BOX_DAMAGE_FACTOR =
            Field.of("DamageFactor", FLOAT, 1.0F, HitBox::damageFactor).alias("DmgFact");
    static final Field<HitBox, String> BOX_NAME = Field.of("Name", TEXT, "", HitBox::name);
    static final Field<HitBox, Boolean> BOX_REACTIVE = Field.of("IsERA", BOOLEAN, false, HitBox::reactiveArmor)
            .alias("ERA");
    static final Field<HitBox, Float> BOX_ERA_EXPLOSION = Field.of("EraExplosion", FLOAT, 0.0F,
            HitBox::eraExplosion).txt(floats(0, 100_000));
    static final Field<HitBox, Float> BOX_ERA_MIN_DAMAGE = Field.of("EraMinDamage", FLOAT, 0.0F,
            HitBox::eraMinDamage).txt(floats(0, 100_000));
    static final EntrySchema<HitBox> HIT_BOX = new EntrySchema<>("bounding box",
            Section.root(BOX_KIND, BOX_POSITION, BOX_SIZE, BOX_DAMAGE_FACTOR, BOX_NAME, BOX_REACTIVE,
                    BOX_ERA_EXPLOSION, BOX_ERA_MIN_DAMAGE).requires(BOX_POSITION).requires(BOX_SIZE),
            v -> new HitBox(v.get(BOX_KIND), v.get(BOX_POSITION), v.get(BOX_SIZE), v.get(BOX_DAMAGE_FACTOR),
                    v.get(BOX_NAME), v.get(BOX_REACTIVE), v.get(BOX_ERA_EXPLOSION), v.get(BOX_ERA_MIN_DAMAGE)));
    /** TXT: {@code BoundingBox} and {@code BoundingEraBox} lines. */
    static final Field<VehicleFile, List<HitBox>> HIT_BOXES = Field.listOf("BoundingBoxes", () -> HIT_BOX,
            VehicleFile::boundingBoxes);

    static final Field<WheelHitBox, Vec3> WHEEL_POSITION =
            Field.of("Position", Vectors.VEC3, null, WheelHitBox::position).alias("Pos");
    static final Field<WheelHitBox, Float> WHEEL_SCALE = Field.of("Scale", FLOAT, 1.0F, WheelHitBox::scale);
    static final EntrySchema<WheelHitBox> WHEEL_HIT_BOX = new EntrySchema<>("wheel",
            Section.root(WHEEL_POSITION, WHEEL_SCALE).requires(WHEEL_POSITION),
            v -> new WheelHitBox(v.get(WHEEL_POSITION), v.get(WHEEL_SCALE)));
    static final Field<Wheels, Float> WHEEL_ROTATION = Field.of("WheelRotation", floats(-10_000, 10_000), 30.0F,
            Wheels::wheelRotation).txt("partwheelrot");
    static final Field<Wheels, Float> TRACK_ROTATION = Field.of("TrackRotation", floats(-10_000, 10_000), 30.0F,
            Wheels::trackRotation).txt("trackrollerrot");

    private HitBoxSchema() {
    }

    static Section<VehicleFile, Wheels> wheels(KindFields kind) {
        return Section.of("Wheels", VehicleFile::wheels, kind.wheels(), WHEEL_ROTATION, TRACK_ROTATION);
    }

    /** Front wheels first, whatever order the file gives them in. */
    static Wheels wheels(Values v, KindFields kind) {
        List<WheelHitBox> hitBoxes = new ArrayList<>(v.get(kind.wheels()));
        hitBoxes.sort((a, b) -> a.position().z > b.position().z ? -1 : 1);
        return new Wheels(hitBoxes, v.get(WHEEL_ROTATION), v.get(TRACK_ROTATION));
    }

    private static HitBoxType boxType(Object raw) {
        String name = Codecs.text(raw).toUpperCase(Locale.ROOT).trim();
        return Arrays.stream(HitBoxType.values()).filter(type -> type.name().equals(name)).findFirst()
                .orElseThrow(() -> new ContentValueException("Invalid bounding box type: " + raw
                        + ". Allowed values: " + Arrays.stream(HitBoxType.values()).map(Enum::name)
                        .collect(Collectors.joining(", "))));
    }
}
