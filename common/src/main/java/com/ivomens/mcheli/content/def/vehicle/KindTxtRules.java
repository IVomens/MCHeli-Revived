package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Parts.Blade;
import com.ivomens.mcheli.content.def.vehicle.Parts.ExhaustFlame;
import com.ivomens.mcheli.content.def.vehicle.Parts.HeliRotor;
import com.ivomens.mcheli.content.def.vehicle.Parts.Plain;
import com.ivomens.mcheli.content.def.vehicle.Parts.Propeller;
import com.ivomens.mcheli.content.def.vehicle.Parts.Pylon;
import com.ivomens.mcheli.content.def.vehicle.Parts.TurretPart;
import com.ivomens.mcheli.content.def.vehicle.Parts.Wing;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.TxtTuple;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.List;

import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.HELI_ROTORS;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.NOZZLES;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.TURRET_PARTS;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.WINGS;

/**
 * Legacy TXT lines that add the parts only some kinds have. Lines that add to the part before them
 * ({@code AddBlade}, {@code AddPartPylon}, {@code AddChildPart}) are ignored when there is none.
 */
final class KindTxtRules {

    /** {@code AddRotor = blades, angle, x, y, z, rx, ry, rz[, foldable]}; {@code AddRotorOld} draws the old way. */
    static final TxtTuple<HeliRotor> ROTOR = heliRotor(false);
    static final TxtTuple<HeliRotor> OLD_ROTOR = heliRotor(true);

    /** {@code AddPartWing = x, y, z, rx, ry, rz, maxRotation}. */
    static final TxtTuple<Wing> WING = TxtTuple.of(KindPartSchemas.WING).exactly(7)
            .vector(KindPartSchemas.WING_PLACE.position()).vector(KindPartSchemas.WING_PLACE.rotation())
            .value(KindPartSchemas.WING_ROTATION)
            .then(PartTxtRules.named(KindPartSchemas.WING_PLACE.name(), "wing", WINGS));
    /** {@code AddPartPylon = x, y, z, rx, ry, rz, maxRotation}: a pylon of the wing before it. */
    static final TxtTuple<Pylon> PYLON = TxtTuple.of(KindPartSchemas.PYLON).minimum(7)
            .vector(KindPartSchemas.PYLON_PLACE.position()).vector(KindPartSchemas.PYLON_PLACE.rotation())
            .value(KindPartSchemas.PYLON_ROTATION)
            .then((pylon, file, count) -> {
                Wing wing = file.get(WINGS).getLast();
                pylon.set(KindPartSchemas.PYLON_PLACE.name(), wing.name() + "_pylon" + wing.pylons().size());
            });
    /** {@code AddPartNozzle = x, y, z, rx, ry, rz}. */
    static final TxtTuple<Plain> NOZZLE = TxtTuple.of(KindPartSchemas.NOZZLE).exactly(6)
            .vector(KindPartSchemas.NOZZLE_PLACE.position()).vector(KindPartSchemas.NOZZLE_PLACE.rotation())
            .then(PartTxtRules.named(KindPartSchemas.NOZZLE_PLACE.name(), "nozzle", NOZZLES));
    /**
     * {@code AddExhaustFlame = model, texture, x, y, z, rx, ry, rz, yaw, delay}: the part is named after its
     * model.
     */
    static final TxtTuple<ExhaustFlame> EXHAUST_FLAME = TxtTuple.of(KindPartSchemas.EXHAUST_FLAME).exactly(10)
            .value(KindPartSchemas.FLAME_PLACE.name())
            .value(KindPartSchemas.FLAME_TEXTURE)
            .vector(KindPartSchemas.FLAME_PLACE.position()).vector(KindPartSchemas.FLAME_PLACE.rotation())
            .value(KindPartSchemas.FLAME_YAW).value(KindPartSchemas.FLAME_DELAY);

    /** {@code AddPart = drawFirstPerson, yaw, pitch, type, x, y, z[, recoil]}. */
    static final TxtTuple<TurretPart> TURRET_PART = turretPart()
            .then(PartTxtRules.named(KindPartSchemas.TURRET_PLACE.name(), "part", TURRET_PARTS));
    /** {@code AddChildPart}, the same values: a part that moves with the part before it. */
    static final TxtTuple<TurretPart> CHILD_PART = turretPart()
            .then((part, file, count) -> {
                TurretPart parent = file.get(TURRET_PARTS).getLast();
                part.set(KindPartSchemas.TURRET_PLACE.name(), parent.name() + "_" + parent.children().size());
            });

    private KindTxtRules() {
    }

    static void register(Schema.Builder<VehicleFile> builder, VehicleKind kind) {
        switch (kind) {
            case HELICOPTER -> builder.txtRule("addrotor", ROTOR.appendingTo(HELI_ROTORS))
                    .txtRule("addrotorold", OLD_ROTOR.appendingTo(HELI_ROTORS));
            case PLANE -> {
                planeParts(builder, ComponentsSchema.PLANE_ROTORS);
                builder.txtRule("addexhaustflame", EXHAUST_FLAME.appendingTo(ComponentsSchema.EXHAUST_FLAMES));
            }
            case SHIP -> planeParts(builder, ComponentsSchema.SHIP_ROTORS);
            case VEHICLE -> builder.txtRule("addpart", TURRET_PART.appendingTo(TURRET_PARTS))
                    .txtRule("addchildpart", (values, text, context) -> {
                        if (!values.get(TURRET_PARTS).isEmpty()) {
                            int index = values.get(TURRET_PARTS).getLast().children().size();
                            CHILD_PART.read(text, values, index, context).ifPresent(child ->
                                    values.updateLast(TURRET_PARTS, parent -> parent.withChild(child)));
                        }
                    });
            case TANK -> {
            }
        }
    }

    /** Propellers, wings and nozzles of planes and ships. */
    private static void planeParts(Schema.Builder<VehicleFile> builder, Field<Components, List<Propeller>> rotors) {
        TxtTuple<Propeller> propeller = TxtTuple.of(KindPartSchemas.PROPELLER).minimum(6)
                .vector(KindPartSchemas.PROPELLER_PLACE.position())
                .vector(KindPartSchemas.PROPELLER_PLACE.rotation())
                .value(KindPartSchemas.PROPELLER_TILT)
                .then(PartTxtRules.named(KindPartSchemas.PROPELLER_PLACE.name(), "rotor", rotors));
        TxtTuple<Blade> blade = TxtTuple.of(KindPartSchemas.BLADE).exactly(8)
                .value(KindPartSchemas.BLADE_COUNT).value(KindPartSchemas.BLADE_ANGLE)
                .vector(KindPartSchemas.BLADE_PLACE.position()).vector(KindPartSchemas.BLADE_PLACE.rotation())
                .then((part, file, count) -> part.set(KindPartSchemas.BLADE_PLACE.name(),
                        "blade" + (file.get(rotors).size() - 1)));
        builder.txtRule("addpartrotor", propeller.appendingTo(rotors))
                .txtRule("addblade", (values, text, context) -> {
                    if (!values.get(rotors).isEmpty()) {
                        blade.read(text, values, values.get(rotors).getLast().blades().size(), context)
                                .ifPresent(read -> values.updateLast(rotors, rotor -> rotor.withBlade(read)));
                    }
                })
                .txtRule("addpartwing", WING.appendingTo(WINGS))
                .txtRule("addpartpylon", (values, text, context) -> {
                    if (!values.get(WINGS).isEmpty()) {
                        PYLON.read(text, values, values.get(WINGS).getLast().pylons().size(), context)
                                .ifPresent(pylon -> values.updateLast(WINGS, wing -> wing.withPylon(pylon)));
                    }
                })
                .txtRule("addpartnozzle", NOZZLE.appendingTo(NOZZLES));
    }

    private static TxtTuple<HeliRotor> heliRotor(boolean oldRenderer) {
        return TxtTuple.of(KindPartSchemas.HELI_ROTOR).exactly(8, 9)
                .fixed(KindPartSchemas.ROTOR_OLD_RENDERER, oldRenderer)
                .value(KindPartSchemas.ROTOR_BLADES).value(KindPartSchemas.ROTOR_BLADE_ANGLE)
                .vector(KindPartSchemas.ROTOR_PLACE.position()).vector(KindPartSchemas.ROTOR_PLACE.rotation())
                .value(KindPartSchemas.ROTOR_FOLDABLE)
                .then(PartTxtRules.named(KindPartSchemas.ROTOR_PLACE.name(), "blade", HELI_ROTORS));
    }

    private static TxtTuple<TurretPart> turretPart() {
        return TxtTuple.of(KindPartSchemas.TURRET_PART).minimum(7)
                .value(KindPartSchemas.TURRET_DRAW_FIRST_PERSON)
                .value(KindPartSchemas.TURRET_YAW).value(KindPartSchemas.TURRET_PITCH)
                .value(KindPartSchemas.TURRET_TYPE)
                .vector(KindPartSchemas.TURRET_PLACE.position())
                .value(KindPartSchemas.TURRET_RECOIL);
    }
}
