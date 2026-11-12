package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Parts.Blade;
import com.ivomens.mcheli.content.def.vehicle.Parts.ExhaustFlame;
import com.ivomens.mcheli.content.def.vehicle.Parts.HeliRotor;
import com.ivomens.mcheli.content.def.vehicle.Parts.Plain;
import com.ivomens.mcheli.content.def.vehicle.Parts.Propeller;
import com.ivomens.mcheli.content.def.vehicle.Parts.Pylon;
import com.ivomens.mcheli.content.def.vehicle.Parts.TurretPart;
import com.ivomens.mcheli.content.def.vehicle.Parts.Wing;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/**
 * The parts only some kinds have: helicopter rotors ({@code HeliRotor}), propellers ({@code PlaneRotor},
 * {@code ShipRotor}) with their blades, wings with their pylons, nozzles, exhaust flames, and the turret
 * parts of ground vehicles ({@code Vpart}).
 * <pre>
 * PlaneRotor:
 * - Position: [0.0, 1.0, 5.0]
 *   Rotation: [0.0, 0.0, 1.0]
 *   RotFactor: 90.0             # how far it tilts with VTOL, in degrees
 *   Blades:
 *   - Position: [0.0, 1.0, 5.0]
 *     BladeNum: 3
 *     BladeRot: 120
 * Vpart:
 * - Position: [0.0, 1.5, 0.5]
 *   CanYaw: true
 *   Type: RECOILS_WEAPON        # NORMAL, ROTATES_WEAPON, RECOILS_WEAPON or TYPE_3
 *   Children: [...]             # parts that move with it; unnamed ones are child0, child1, ...
 * </pre>
 */
final class KindPartSchemas {

    static final Placement<HeliRotor> ROTOR_PLACE = Placement.of("blade", HeliRotor::position,
            HeliRotor::rotation, HeliRotor::name);
    static final Field<HeliRotor, Integer> ROTOR_BLADES = Field.of("BladeCount", INT, 1, HeliRotor::blades)
            .alias("Count").alwaysWritten();
    static final Field<HeliRotor, Integer> ROTOR_BLADE_ANGLE = Field.of("BladeRot", INT, 0, HeliRotor::bladeAngle)
            .alias("BladeRotation");
    static final Field<HeliRotor, Boolean> ROTOR_FOLDABLE = Field.of("CanFold", BOOLEAN, false,
            HeliRotor::foldable);
    static final Field<HeliRotor, Boolean> ROTOR_OLD_RENDERER = Field.of("OldRenderer", BOOLEAN, false,
            HeliRotor::oldRenderer);
    static final EntrySchema<HeliRotor> HELI_ROTOR = ROTOR_PLACE.entry("rotor",
            v -> new HeliRotor(ROTOR_PLACE.position(v), ROTOR_PLACE.rotation(v), ROTOR_PLACE.name(v),
                    v.get(ROTOR_BLADES), v.get(ROTOR_BLADE_ANGLE), v.get(ROTOR_FOLDABLE), v.get(ROTOR_OLD_RENDERER)),
            ROTOR_BLADES, ROTOR_BLADE_ANGLE, ROTOR_FOLDABLE, ROTOR_OLD_RENDERER);

    static final Placement<Blade> BLADE_PLACE = Placement.of("blade", Blade::position, Blade::rotation,
            Blade::name);
    static final Field<Blade, Integer> BLADE_COUNT = Field.of("BladeNum", INT, 1, Blade::count).alwaysWritten();
    /** A blade without its angle is left out, as it always was. */
    static final Field<Blade, Integer> BLADE_ANGLE = Field.of("BladeRot", INT, null, Blade::angle).alwaysWritten();
    static final EntrySchema<Blade> BLADE = BLADE_PLACE.entry("blade",
            v -> new Blade(BLADE_PLACE.position(v), BLADE_PLACE.rotation(v), BLADE_PLACE.name(v),
                    v.get(BLADE_COUNT), v.get(BLADE_ANGLE)),
            BLADE_COUNT, BLADE_ANGLE).unsupported("RotFactor");

    static final Placement<Propeller> PROPELLER_PLACE = Placement.of("rotor", Propeller::position,
            Propeller::rotation, Propeller::name);
    /** How far it tilts with VTOL, in degrees. */
    static final Field<Propeller, Float> PROPELLER_TILT = Field.of("RotFactor", floats(-180, 180), 90.0F,
            Propeller::tilt).alwaysWritten();
    static final Field<Propeller, List<Blade>> PROPELLER_BLADES = Field.listOf("Blades", () -> BLADE,
            Propeller::blades);
    static final EntrySchema<Propeller> PROPELLER = PROPELLER_PLACE.entry("propeller",
            v -> new Propeller(PROPELLER_PLACE.position(v), PROPELLER_PLACE.rotation(v), PROPELLER_PLACE.name(v),
                    v.get(PROPELLER_TILT), v.get(PROPELLER_BLADES).stream().filter(b -> b.angle() != null).toList()),
            PROPELLER_TILT, PROPELLER_BLADES);

    static final Placement<Pylon> PYLON_PLACE = Placement.of("pylon", Pylon::position, Pylon::rotation,
            Pylon::name);
    static final Field<Pylon, Float> PYLON_ROTATION = Field.of("MaxRotation", FLOAT, 0.0F, Pylon::maxRotation)
            .alias("MaxRot").alwaysWritten();
    static final EntrySchema<Pylon> PYLON = PYLON_PLACE.entry("pylon",
            v -> new Pylon(PYLON_PLACE.position(v), PYLON_PLACE.rotation(v), pylonName(v), v.get(PYLON_ROTATION)),
            PYLON_ROTATION);

    static final Placement<Wing> WING_PLACE = Placement.of("wing", Wing::position, Wing::rotation, Wing::name);
    static final Field<Wing, Float> WING_ROTATION = Field.of("MaxRotation", floats(-180, 180), 0.0F,
            Wing::maxRotation).alias("MaxRot").txt(FLOAT).alwaysWritten();
    static final Field<Wing, List<Pylon>> WING_PYLONS = Field.listOf("Pylons", () -> PYLON, Wing::pylons);
    static final EntrySchema<Wing> WING = WING_PLACE.entry("wing",
            v -> new Wing(WING_PLACE.position(v), WING_PLACE.rotation(v), WING_PLACE.name(v), v.get(WING_ROTATION),
                    v.get(WING_PYLONS)),
            WING_ROTATION, WING_PYLONS);

    static final Placement<Plain> NOZZLE_PLACE = Placement.of("nozzle", Plain::position, Plain::rotation,
            Plain::name);
    static final EntrySchema<Plain> NOZZLE = NOZZLE_PLACE.entry("nozzle",
            v -> new Plain(NOZZLE_PLACE.position(v), NOZZLE_PLACE.rotation(v), NOZZLE_PLACE.name(v)));

    static final Placement<ExhaustFlame> FLAME_PLACE = Placement.of("exhaust_flame", ExhaustFlame::position,
            ExhaustFlame::rotation, ExhaustFlame::name);
    static final Field<ExhaustFlame, String> FLAME_TEXTURE = Field.of("Texture", TEXT, "", ExhaustFlame::texture);
    static final Field<ExhaustFlame, Float> FLAME_YAW = Field.of("Yaw", FLOAT, 0.0F, ExhaustFlame::yaw);
    static final Field<ExhaustFlame, Integer> FLAME_DELAY = Field.of("Delay", INT, 0, ExhaustFlame::delay);
    static final EntrySchema<ExhaustFlame> EXHAUST_FLAME = FLAME_PLACE.entry("exhaust flame",
            v -> new ExhaustFlame(FLAME_PLACE.position(v), FLAME_PLACE.rotation(v), FLAME_PLACE.name(v),
                    v.get(FLAME_TEXTURE), v.get(FLAME_YAW), v.get(FLAME_DELAY)),
            FLAME_TEXTURE, FLAME_YAW, FLAME_DELAY);

    static final Placement<TurretPart> TURRET_PLACE = Placement.of("part", TurretPart::position,
            TurretPart::rotation, TurretPart::name);
    static final Field<TurretPart, Boolean> TURRET_DRAW_FIRST_PERSON = Field.of("DrawFP", BOOLEAN, true,
            TurretPart::drawFirstPerson).alias("DrawFirstPerson").alwaysWritten();
    static final Field<TurretPart, Boolean> TURRET_YAW = Field.of("CanYaw", BOOLEAN, false, TurretPart::yaw)
            .alias("UnlockYaw").alwaysWritten();
    static final Field<TurretPart, Boolean> TURRET_PITCH = Field.of("CanPitch", BOOLEAN, false, TurretPart::pitch)
            .alias("UnlockPitch").alwaysWritten();
    static final Field<TurretPart, Integer> TURRET_TYPE = Field.of("Type", VehicleCodecs.TURRET_PART_TYPE, 0,
            TurretPart::type).alwaysWritten();
    static final Field<TurretPart, Float> TURRET_RECOIL = Field.of("RecoilBuff", FLOAT, 0.0F,
            TurretPart::recoilBuffer);
    /** TXT: {@code AddChildPart} lines, which add to the part before them. */
    static final Field<TurretPart, List<TurretPart>> TURRET_CHILDREN = Field.listOf("Children",
            () -> KindPartSchemas.TURRET_PART, TurretPart::children);
    static final EntrySchema<TurretPart> TURRET_PART = TURRET_PLACE.entry("vehicle part",
            KindPartSchemas::turretPart, TURRET_DRAW_FIRST_PERSON, TURRET_YAW, TURRET_PITCH, TURRET_TYPE,
            TURRET_RECOIL, TURRET_CHILDREN);

    private KindPartSchemas() {
    }

    /** An unnamed pylon is named after its wing and its place: {@code wing0_pylon1}. */
    private static String pylonName(Values v) {
        String written = v.get(PYLON_PLACE.name());
        int wing = v.parent().map(Values::index).orElse(0);
        return written != null ? written : "wing" + wing + "_pylon" + v.index();
    }

    /** An unnamed turret part is {@code part<n>}; an unnamed child of the n-th part is {@code child<n>}. */
    private static TurretPart turretPart(Values v) {
        String written = v.get(TURRET_PLACE.name());
        String name = written != null ? written
                : isTopLevel(v) ? "part" + v.index() : "child" + topLevelIndex(v);
        return new TurretPart(TURRET_PLACE.position(v), TURRET_PLACE.rotation(v), name,
                v.get(TURRET_DRAW_FIRST_PERSON), v.get(TURRET_YAW), v.get(TURRET_PITCH), v.get(TURRET_TYPE),
                v.get(TURRET_RECOIL), v.get(TURRET_CHILDREN));
    }

    private static boolean isTopLevel(Values v) {
        return v.parent().flatMap(Values::parent).isEmpty();
    }

    private static int topLevelIndex(Values v) {
        Values top = v;
        while (!isTopLevel(top)) {
            top = top.parent().orElseThrow();
        }
        return top.index();
    }
}
