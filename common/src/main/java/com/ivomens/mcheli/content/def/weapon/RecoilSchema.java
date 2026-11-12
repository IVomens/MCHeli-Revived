package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Recoil;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/**
 * YAML {@code Recoil}: how the shot kicks the vehicle and the view.
 * <p>
 * The recoil buffer smooths the kick over {@code BufferCount} ticks, recovering at {@code BufferSpeed}, which
 * is never more than half the count. A file without a Recoil section keeps the legacy defaults (count 2, speed
 * 3); a Recoil section starts both at 1, as it always has. Legacy TXT sets both with one line,
 * {@code RecoilBufCount = count, speed}; see {@link WeaponTxtRules#recoilBuffer}.
 */
final class RecoilSchema {

    static final Field<Recoil, Float> POWER = Field.of("Power", floats(0, 100), 0.0F, Recoil::power).txt("recoil");
    /** Vertical kick of the view. */
    static final Field<Recoil, Float> PITCH = Field.of("Pitch", FLOAT, 0.0F, Recoil::pitch).txt("recoilpitch");
    /** Horizontal kick, always to the same side. */
    static final Field<Recoil, Float> YAW = Field.of("Yaw", FLOAT, 0.0F, Recoil::yaw).txt("recoilyaw");
    /** Random extra vertical kick. */
    static final Field<Recoil, Float> PITCH_RANGE =
            Field.of("PitchRange", FLOAT, 0.0F, Recoil::pitchRange).txt("recoilpitchrange");
    /** Random horizontal kick, to either side. */
    static final Field<Recoil, Float> YAW_RANGE =
            Field.of("YawRange", FLOAT, 0.0F, Recoil::yawRange).txt("recoilyawrange");
    static final Field<Recoil, Float> RECOVER_FACTOR =
            Field.of("RecoverFactor", FLOAT, 0.8F, Recoil::recoverFactor).txt("recoilrecoverfactor");
    static final Field<Recoil, Integer> BUFFER_COUNT =
            Field.of("BufferCount", INT, 2, Recoil::bufferCount);
    static final Field<Recoil, Integer> BUFFER_SPEED =
            Field.of("BufferSpeed", INT, 3, Recoil::bufferSpeed);

    static final Section<WeaponDefinition, Recoil> SECTION = Section.of("Recoil", WeaponDefinition::recoil,
                    POWER, PITCH, YAW, PITCH_RANGE, YAW_RANGE, RECOVER_FACTOR, BUFFER_COUNT, BUFFER_SPEED)
            .onYamlEntry(values -> {
                values.set(BUFFER_COUNT, 1);
                values.set(BUFFER_SPEED, 1);
            })
            .afterYaml(RecoilSchema::limitBufferSpeed);

    private RecoilSchema() {
    }

    static Recoil build(Values v) {
        return new Recoil(v.get(POWER), v.get(PITCH), v.get(YAW), v.get(PITCH_RANGE), v.get(YAW_RANGE),
                v.get(RECOVER_FACTOR), v.get(BUFFER_COUNT), v.get(BUFFER_SPEED));
    }

    /** The buffer never recovers faster than half its length. */
    static void limitBufferSpeed(Values values) {
        int halfCount = values.get(BUFFER_COUNT) / 2;
        if (values.get(BUFFER_SPEED) > halfCount) {
            values.set(BUFFER_SPEED, halfCount);
        }
    }
}
