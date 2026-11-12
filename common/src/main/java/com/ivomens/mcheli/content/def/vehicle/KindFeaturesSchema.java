package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.KindFeatures.Vtol;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/**
 * The section of each kind's own settings:
 * <pre>
 * HeliFeatures: { IsFoldableBlade: true }
 * PlaneFeatures:                         # ShipFeatures has the same keys
 *   VariableSweepWing: true
 *   SweepWingSpeed: 1.2
 *   EnableVtol: { IsDefault: false, Yaw: 0.3, Pitch: 0.2 }   # or EnableVtol: true
 * TankFeatures: { WeightType: TANK, WeightedCenterZ: -0.5 }  # UNKNOWN, CAR or TANK
 * VehicleFeatures: { CanMove: true, CanRotate: true }
 * </pre>
 * The emitter always writes the helicopter, plane, ship and tank sections in full, so a converted file shows
 * them.
 */
final class KindFeaturesSchema {

    static final Field<KindFeatures, Boolean> FOLDABLE_BLADES =
            Field.of("IsFoldableBlade", BOOLEAN, false, KindFeatures::foldableBlades).txt("enablefoldblade")
            .alwaysWritten();

    static final Field<KindFeatures, Boolean> VARIABLE_SWEEP_WING = Field.of("VariableSweepWing", BOOLEAN, false,
            KindFeatures::variableSweepWing).txt("variablesweepwing")
            .alwaysWritten();
    /** The top speed with the wing swept back; scaled by the server's speed setting like the speed. */
    static final Field<KindFeatures, Float> SWEPT_SPEED = Field.of("SweepWingSpeed", floats(0, 5), 0.1F,
            KindFeatures::sweptSpeed).txt("sweepwingspeed")
            .alwaysWritten();
    static final Field<Vtol, Boolean> VTOL = Field.of("Enabled", BOOLEAN, false, Vtol::enabled).txt("enablevtol")
            .alwaysWritten();
    static final Field<Vtol, Boolean> VTOL_BY_DEFAULT = Field.of("IsDefault", BOOLEAN, false, Vtol::byDefault)
            .txt("defaultvtol");
    static final Field<Vtol, Float> VTOL_YAW = Field.of("Yaw", floats(0, 1), 0.3F, Vtol::yaw).txt("vtolyaw");
    static final Field<Vtol, Float> VTOL_PITCH = Field.of("Pitch", floats(0.01F, 1), 0.2F, Vtol::pitch)
            .txt("vtolpitch");
    static final Section<KindFeatures, Vtol> VTOL_SECTION = Section.of("EnableVtol", KindFeatures::vtol, VTOL,
            VTOL_BY_DEFAULT, VTOL_YAW, VTOL_PITCH).enabledBy(VTOL).shorthand(VTOL);

    static final Field<KindFeatures, Integer> WEIGHT_TYPE = Field.of("WeightType", VehicleCodecs.TANK_WEIGHT, 0,
            KindFeatures::weightType).txt("weighttype")
            .alwaysWritten();
    static final Field<KindFeatures, Float> WEIGHTED_CENTER_Z = Field.of("WeightedCenterZ", floats(-1000, 1000),
            0.0F, KindFeatures::weightedCenterZ).alias("CenterZ").txt("weightedcenterz")
            .alwaysWritten();

    static final Field<KindFeatures, Boolean> CAN_MOVE =
            Field.of("CanMove", BOOLEAN, false, KindFeatures::canMove).txt("canmove");
    static final Field<KindFeatures, Boolean> CAN_ROTATE =
            Field.of("CanRotate", BOOLEAN, false, KindFeatures::canRotate).txt("canrotation");

    private KindFeaturesSchema() {
    }

    static Section<VehicleFile, KindFeatures> section(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> section("HeliFeatures", FOLDABLE_BLADES);
            case PLANE -> section("PlaneFeatures", VARIABLE_SWEEP_WING, SWEPT_SPEED, VTOL_SECTION)
                    .unsupported("EnableAutoPilot");
            case SHIP -> section("ShipFeatures", VARIABLE_SWEEP_WING, SWEPT_SPEED, VTOL_SECTION)
                    .unsupported("EnableAutoPilot");
            case TANK -> section("TankFeatures", WEIGHT_TYPE, WEIGHTED_CENTER_Z);
            case VEHICLE -> section("VehicleFeatures", CAN_MOVE, CAN_ROTATE);
        };
    }

    /** The values of every kind; those another kind's section declares keep their defaults. */
    static KindFeatures build(Values v) {
        return new KindFeatures(v.get(FOLDABLE_BLADES), v.get(VARIABLE_SWEEP_WING), v.get(SWEPT_SPEED),
                new Vtol(v.get(VTOL), v.get(VTOL_BY_DEFAULT), v.get(VTOL_YAW), v.get(VTOL_PITCH)),
                v.get(WEIGHT_TYPE), v.get(WEIGHTED_CENTER_Z), v.get(CAN_MOVE), v.get(CAN_ROTATE));
    }

    @SafeVarargs
    private static Section<VehicleFile, KindFeatures> section(String key, Member<KindFeatures>... members) {
        return Section.of(key, VehicleFile::kindFeatures, members);
    }
}
